import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Server extends Client{
    private boolean up = false; //标记服务端是否已启用
    private boolean connected = false;

    //设置一个接受缓冲区

    /**
     * 根据当前报文中window值设置window大小
     * */
    public void setWindowSize() {
        window.size = Transformer.toInteger(receivedPacket.getWindow());
    }
    /**
     * 启用服务器
     * */
    public void start() throws Exception{
        up = true;
        sentPacket = receivedPacket = null;
        welcomeSocket = new ServerSocket(port);
        System.out.printf("Server started! Listening on %d...\n", port);
    }
    /**
     * 关闭服务器
     * */
    public void close() {
        up = false;
    }
    public void release() throws Exception{
        connected = false;
        connectionSocket = null;
        receivedPacket = sentPacket = null;
        //welcomeSocket.close();
        System.out.println("release connection with client");
        start();
        System.out.println();
    }
    public boolean connect() {
        return connected;
    }
    /***
     * 对服务器状态作检查，如是否处于启用状态，监听端口是否为合法端口（-1即不合法端口）等
     * @return boolean
     */
    public boolean check() {
        return up && port >= 0;
    }

    /**
     * 监听某个端口
     * @param port 监听端口
     * */
    public void listen(int port) {
        this.port = port;
    }
    private static void buildConnection(Server server) throws Exception{
        while (!server.connected) {
            //监听以接收报文
            server.connectionSocket = server.welcomeSocket.accept();// 先主动建立一个用于接收初始请求报文的socket，只用于建立连接前的冷启动
            Socket listenSocket = server.connectionSocket;
            InputStream bytesFromClient = listenSocket.getInputStream();// 获取client的字节流
            OutputStream bytesToClient = listenSocket.getOutputStream();
            byte[] buffer = server.getBytes(bytesFromClient, 24);// buffer中存放client发来报文的字节形式
            Packet firstHandShake = server.buildPacket(buffer);// 组装报文
            server.receive(firstHandShake);// 收到client的第一次握手报文
            System.out.println("first from client");
            server.print();// 回显报文

            if (server.checkSYN(firstHandShake)) {
                System.out.println("start ack first handshake");
                //应答，作为第二次握手的报文
                int seq = new Random().nextInt(123456789);// 该报文第一个字节id
                byte[] id = Transformer.toBytes(seq, 4);
                int ack = Transformer.toInteger(server.receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
//                System.out.printf("ack=%d\n", ack);
                byte[] Ack = Transformer.toBytes(ack, 4);
                byte[] spcBytes = new byte[2];
                spcBytes[0] = server.receivedPacket.getSpcBytes()[0];
                spcBytes[1] = (byte) (server.receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1
                int checkSum = 0;
                Packet secondShakeHand = new Packet(
                        server.receivedPacket.getDest(), server.receivedPacket.getSrc(), id, Ack,
                        spcBytes, server.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                        server.receivedPacket.getUrgent(), server.receivedPacket.getOptions(),
                        server.receivedPacket.getAlign(), server.MSS
                );
//                System.out.println("Server MSs = " + secondShakeHand.MSS);
                System.out.println("second from server:");
                System.out.println(secondShakeHand);
                server.send(listenSocket, secondShakeHand);// 将报文发送给client，完成应答

                //检查第三次握手报文
                bytesFromClient = listenSocket.getInputStream();
                buffer = server.getBytes(bytesFromClient, 24);
                Packet thirdHandShake = server.buildPacket(buffer);
                server.receive(thirdHandShake);
                System.out.println("third from client:");
                server.print();
                int clintSeq = Transformer.toInteger(server.receivedPacket.getId()), clientAck = -1;
                if (server.checkACK(thirdHandShake)) {
                    clientAck = Transformer.toInteger(server.receivedPacket.getAck());
                } else {
                    System.out.println("Failed to get connected because of ACK=0.");
                    continue;
                }
                if (clintSeq == ack && clientAck == seq + 1) {
                    // 符合第三次握手的报文规范，可以建立连接
                    server.connected = true;
                    System.out.println("Connected to Client.");
                    server.setWindowSize();
                    server.window.initPackets();
                } else {
                    System.out.printf("clientSeq=%d, ack=%d, clientAck=%d, seq=%d\n", clintSeq, ack, clientAck, seq);
                    System.out.println("Failed to get connected because of ACK or ack.");
                    //告诉client连接失败，send RST=1
                    spcBytes[1] = (byte) (spcBytes[1] | (1 << 2));// RST置1
                    Packet failure = new Packet(
                            server.receivedPacket.getDest(), server.receivedPacket.getSrc(), id, Ack,
                            spcBytes, server.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                            server.receivedPacket.getUrgent(), server.receivedPacket.getOptions(),
                            server.receivedPacket.getAlign(), server.MSS
                    );
                    bytesToClient.write(failure.getBytes()); // 通知client连接失败
                }
            } else {
                System.out.println("wrong when server try to build connection");
                continue;// 对于未建立连接时的一切SYN != 1 || ACK != 0的报文直接丢弃}
            }
//            bytesFromClient.close();
//            bytesToClient.close();
        }
    }
    /**
     * 挥手释放连接
     * */
    private static void releaseConnection(Server server, Socket connectingSocket) throws Exception {
        //  Socket connectingSocket = server.welcomeSocket.accept();//连接中的socket
        InputStream bytesFromClient = connectingSocket.getInputStream();
        OutputStream bytesToClient = connectingSocket.getOutputStream();//暂未使用
        byte[] buffer = server.getBytes(bytesFromClient, 24);
        Packet firstHandShake = server.buildPacket(buffer);// 组装报文
        server.receive(firstHandShake);// 收到client的第一次挥手报文
        server.print();// 回显报文

        if (server.checkFIN(firstHandShake)) { //检查第一次挥手报文
            //应答，作为第二次挥手的报文
            int seq = new Random().nextInt(123456789);// 该报文第一个字节id
            byte[] id = Transformer.toBytes(seq, 4);
            int ack = Transformer.toInteger(server.receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
            byte[] Ack = Transformer.toBytes(ack, 4);
            byte[] spcBytes = new byte[2];
            spcBytes[0] = server.receivedPacket.getSpcBytes()[0];
            spcBytes[1] = (byte) (server.receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1
            spcBytes[1] = (byte) (spcBytes[1] & 0b11111110);// FIN置0

            int checkSum = 0;
            Packet secondShakeHand = new Packet(
                    server.receivedPacket.getDest(), server.receivedPacket.getSrc(), id, Ack,
                    spcBytes, server.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                    server.receivedPacket.getUrgent(), server.receivedPacket.getOptions(),
                    server.receivedPacket.getAlign(), server.receivedPacket.MSS
            );
            server.send(connectingSocket, secondShakeHand);// 将报文发送给client，完成应答
            //todo:data transfer
            // 第二次挥手报文发送完毕，client断开连接，server将剩下的数据传输完毕；

            //数据传输完毕，组装第三次挥手报文
            seq = new Random().nextInt(123);// todo：该报文第一个字节id，应为传输数据完毕后的下一个序号，暂时填充为随机
            id = Transformer.toBytes(seq, 4);
            spcBytes[1] = (byte) (spcBytes[1] | 0b00000001);
            //更换seq，FIN置回1，其它部分报文不变
            Packet thirdHandShake = new Packet(
                    server.receivedPacket.getDest(), server.receivedPacket.getSrc(), id, Ack,
                    spcBytes, server.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                    server.receivedPacket.getUrgent(), server.receivedPacket.getOptions(),
                    server.receivedPacket.getAlign(), server.receivedPacket.MSS
            );

            while (server.connected) {
                server.send(connectingSocket, thirdHandShake);// 将报文发送给client，请求断开连接；
                //接受第四次挥手报文
                bytesFromClient = connectingSocket.getInputStream();
                buffer = server.getBytes(bytesFromClient, 24);
                Packet fourthHandShake = server.buildPacket(buffer);
                server.receive(fourthHandShake);
                server.print();
                //回显报文
                if (server.checkACK(fourthHandShake) &&
                        Transformer.toInteger(server.receivedPacket.getId()) == ack + 1 &&
                        Transformer.toInteger(server.receivedPacket.getAck()) == seq + 1) {
                    //符合挥手规范，释放连接
                    server.connected = false;
                    System.out.println("Connection released.");
                    connectingSocket.close();
                } else {
                    System.out.println("Failed to release connection.");
                }//不符合挥手规范，释放连接失败
            }
        } else {
            System.out.println("Failed to release connection.");
        }//不符合挥手规范，释放连接失败，抛出异常
    }


    public Server() {
        super();
    }


    public static void main(String[] argv) throws Exception{
        //argv[0]为服务器启动时监听的端口号; argv[1] argv[2]分别为传输文件的path和MSS(需要判断是否存在)
        //todo
        Server server = new Server();
        if (argv.length != 3) {
            System.out.println("Parameter count error!");
            return;
        }// 参数数量是否合法

        try {
            server.listen(Integer.parseInt(argv[0]));
            if (server.port < 0 || server.port > 65535) {
                System.out.printf("Invalid port '%s'.\n", argv[0]);
                return;
            }
        }catch (NumberFormatException e) {
            System.out.println("Invalid port.");
            return;
        }// 端口号是否合法

        String path = argv[1];
        File file = new File(path);
        try (FileInputStream inputFromFile = new FileInputStream(file)){
            ;
        }catch (FileNotFoundException e) {
            System.out.printf("File '%s' not found.\n", argv[1]);
            return;
        }// 文件是否存在

        try {
            server.MSS = Integer.parseInt(argv[2]);
            if (server.MSS > 1500 || server.MSS <= 0) {
                System.out.printf("Invalid MSS '%s'.\n", argv[2]);
                return;
            }
            server.window.segmentSize = server.MSS;
        }catch (NumberFormatException e) {
            System.out.printf("Invalid MSS '%s'.\n", argv[2]);
            return;
        }// MSS是否合法
        server.start();

        while (server.check()) {
            //执行服务器逻辑
            while (!server.connect()){
                try {
                    Server.buildConnection(server);
                    break;
                } catch (Exception e) {
                    System.out.println("Connected failed.");
                }
            }
            //finish connecting
            Socket connectionSocket = server.connectionSocket;
            //Data Transfer
            while (connectionSocket.isConnected()){
                try {
//                    String hello = "hello";
//                    OutputStream o = connectionSocket.getOutputStream();
//                    o.write(hello.getBytes());
//                    o.flush();
                    Server.dataTransfer(server, connectionSocket, path);
                }catch (Exception e) {
                    server.release();
//                    return;
                    break;
                }
            }
            //todo release connection
        }
        server.close();
        System.out.println("Server shut down unexpectedly.");
    }
}


