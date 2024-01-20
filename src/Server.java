import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Server extends Client{
    private boolean up = false; //标记服务端是否已启用
    private boolean connected = false;
    public String path;


    //设置一个接受缓冲区

    /**
     * 根据当前报文中window值设置window大小
     * */
    public void setWindowSize() {
        window.setSize(Transformer.toInteger(receivedPacket.getWindow()));
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
    public void close() throws Exception{
        up = false;
        connected = false;
        welcomeSocket.close();
    }
    public void release() throws Exception{
        connected = false;
        connectionSocket = null;
        receivedPacket = sentPacket = null;
        welcomeSocket.close();
        System.out.println("release connection with client");
        start();
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
            byte[] buffer = server.getBytes(bytesFromClient);// buffer中存放client发来报文的字节形式
            Packet firstHandShake = server.buildPacket(buffer);// 组装报文
            server.receive(firstHandShake);// 收到client的第一次握手报文
            System.out.println("first from client");
            server.print();// 回显报文

            if (server.checkSYN(firstHandShake)) {
                System.out.println("start ack first handshake");
                System.out.println();
                //应答，作为第二次握手的报文
                int seq = new Random().nextInt(123);// 该报文第一个字节id
                byte[] id = Transformer.toBytes(seq, 4);
                int ack = Transformer.toInteger(server.receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
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
                server.send(listenSocket, secondShakeHand);// 将报文发送给client，完成应答

                //检查第三次握手报文
                bytesFromClient = listenSocket.getInputStream();
                buffer = server.getBytes(bytesFromClient);
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
        }
    }
    /**
     * 挥手释放连接
     * */
    private static void releaseConnection(Server server, Socket connectingSocket) throws Exception {
        InputStream bytesFromClient = connectingSocket.getInputStream();
        byte[] buffer = server.getBytes(bytesFromClient);
        Packet firstHandShake = server.buildPacket(buffer);// 组装报文
        server.receive(firstHandShake);// 收到client的第一次挥手报文
        System.out.println("first from client:");
        server.print();// 回显报文

        if (server.checkFIN(firstHandShake)) { //检查第一次挥手报文
            //应答，作为第二次挥手的报文
            int seq = new Random().nextInt(123);
            byte[] id = Transformer.toBytes(seq, 4);
            int ack = Transformer.toInteger(server.receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
            byte[] Ack = Transformer.toBytes(ack, 4);
            byte[] spcBytes = new byte[2];
            spcBytes[0] = server.receivedPacket.getSpcBytes()[0];
//            spcBytes[1] = (byte) 0b00010001;// FIN置1
            spcBytes[1] = (byte) (server.receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1

            int checkSum = 0;
            Packet secondShakeHand = new Packet(
                    server.receivedPacket.getDest(), server.receivedPacket.getSrc(),
                    id,
                    Ack,
                    spcBytes, server.receivedPacket.getWindow(),
                    Transformer.toBytes(checkSum, 2), server.receivedPacket.getUrgent(),
                    server.receivedPacket.getOptions(), server.receivedPacket.getAlign(),
                    server.receivedPacket.MSS
            );
            server.send(connectingSocket, secondShakeHand);// 将报文发送给client，完成应答
            System.out.println("second from server:");
            System.out.println(secondShakeHand);
            //data transfer
            Thread.sleep(500);
            System.out.println("---DATA---");
            Thread.sleep(500);

            //确认数据传输完毕
            while (true) {
                bytesFromClient = connectingSocket.getInputStream();
                Packet ctrl = server.buildPacket(server.getBytes(bytesFromClient));
                if (Controller.check(ctrl) == Controller.RELEASE) {
                    break;
                }else {
                    Thread.sleep(500);
                    System.out.println("---DATA---");
                    Thread.sleep(500);
                }
            }

            //数据传输完毕，组装第三次挥手报文
            seq = new Random().nextInt(123);//该报文第一个字节id，应为传输数据完毕后的下一个序号，填充为随机
            id = Transformer.toBytes(seq, 4);
            spcBytes[1] = (byte) (spcBytes[1] | 0b00000001);
            //更换seq，FIN置回1，其它部分报文不变
            Packet thirdHandShake = new Packet(
                    server.receivedPacket.getDest(), server.receivedPacket.getSrc(),
                    id,
                    Ack,
                    spcBytes, server.receivedPacket.getWindow(),
                    Transformer.toBytes(checkSum, 2), server.receivedPacket.getUrgent(),
                    server.receivedPacket.getOptions(), server.receivedPacket.getAlign(),
                    server.receivedPacket.MSS
            );
            while (server.connected) {
                server.send(connectingSocket, thirdHandShake);// 将报文发送给client，请求断开连接；
                System.out.println("third from server:");
                System.out.println(thirdHandShake);
                //接受第四次挥手报文
                bytesFromClient = connectingSocket.getInputStream();
                buffer = server.getBytes(bytesFromClient);
                Packet fourthHandShake = server.buildPacket(buffer);
                server.receive(fourthHandShake);
                System.out.println("fourth from client:");
                server.print();
                //回显报文
                if (server.checkACK(fourthHandShake)) {
                    //符合挥手规范，释放连接
                    //根据tcp, 收到既可以释放连接，不必再做检查
                    server.connected = false;
                    System.out.println("Connection released.");
                    connectingSocket.close();
                } else {
                    System.out.println("Failed to release connection!");
                }//不符合挥手规范，释放连接失败
            }
        } else {
            System.out.println("Failed to release connection!!");
        }//不符合挥手规范，释放连接失败，抛出异常
    }


    public Server() {
        super();
    }
    public Packet set(Socket cont, Packet ctrl) throws Exception{
        int ctr = Controller.check(ctrl);
        Data p = ctrl.getData();
        byte[] data = p.getData();
        String tmp = new String(data);
        if (ctr == Controller.PATH_SET) {
            File file = new File(tmp);
            Packet ack = new Packet();
            try (FileInputStream inputFromFile = new FileInputStream(file)){
                path = tmp;
                System.out.printf("path '%s' has been set!\n", path);
                ack.setData(new Data(String.format("path '%s' has been set!\n", path).getBytes()));
                ack.setMessage(Error.NO_ERROR);
            }catch (FileNotFoundException e) {
                System.out.printf("File '%s' not found.\n", tmp);
                ack.setData(new Data(String.format("File '%s' not found.\n", tmp).getBytes()));
                ack.setMessage(Error.WRONG_MSG);
            }
            return ack;
        }else if (ctr == Controller.MSS_SET) {
            if (window.getSize() / Integer.parseInt(tmp) <= 0) {
                String ack = "MSS set failed because of too large MSS.";
                System.out.println(ack);
                ctrl.setMessage(Error.WRONG_MSG);
                ctrl.setData(new Data(ack.getBytes()));
                return ctrl;
            }
            MSS = Integer.parseInt(tmp);
            window.setSegmentSize(MSS);
            System.out.printf("MSS = %d has been set!\n", MSS);
            ctrl.setMessage(Error.NO_ERROR);
            ctrl.setData(new Data(String.format("MSS = %d has been set!", MSS).getBytes()));
            return ctrl;
        }else if (ctr == Controller.WINDOW_SET) {
            if (Integer.parseInt(tmp) / window.getSegmentSize() <= 0) {
                String ack = "window set failed because of too small window size.";
                System.out.println(ack);
                ctrl.setMessage(Error.WRONG_MSG);
                ctrl.setData(new Data(ack.getBytes()));
                return ctrl;
            }
            window.setSize(Integer.parseInt(tmp));
            System.out.printf("window size = %d has been set!\n", window.getSize());
            ctrl.setDestPort(ctrl.getSrc());
            ctrl.setSrcPort(ctrl.getDest());
            ctrl.setWindow(Transformer.toBytes(window.getSize(), 2));
            ctrl.setMessage(Error.NO_ERROR);
            ctrl.setData(new Data(String.format("window size = %d has been set!", window.getSize()).getBytes()));
            return ctrl;
        }
        return null;
    }
    public void errorManage(Packet icmp) {
        //todo
    }
    public static void main(String[] argv) throws Exception{
        //argv[0]为服务器启动时监听的端口号;
        Server server = new Server();
        if (argv.length != 1) {
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

        while (true){
            try {
                server.start();
                while (server.check()) {
                    //执行服务器逻辑
                    while (!server.connect()) {
                        try {
                            Server.buildConnection(server);
                            break;
                        } catch (Exception e) {
                            System.out.println("Connected failed.");
                        }
                    }
                    //finish connecting
                    Socket connectionSocket = server.connectionSocket;
                    while (server.connectionSocket != null) {
                        InputStream fromClient = connectionSocket.getInputStream();
                        Packet rcv = server.buildPacket(server.getBytes(fromClient));
                        int check = Controller.check(rcv);
                        switch (check) {
                            case Controller.MSS_SET:
                            case Controller.PATH_SET:
                            case Controller.WINDOW_SET:
                                Packet tmp = server.set(connectionSocket, rcv);
                                if (tmp != null)
                                    server.send(connectionSocket, tmp);
                                break;
                            case Error.WRONG_MSG:
                            case Error.TIME_OUT:
                            case Error.MISS_MSG:
                            case Error.SHUFFLE_MSG:
                                server.errorManage(rcv);
                                break;
                            case Controller.DATA_TRANSFER:
                                Server.dataTransfer(server, connectionSocket, server.path);
                                server.window.initPackets();//完成传输后window不再使用，清空，保证每次传输时window均为空
                                break;
                            case Controller.RELEASE:
                                //release
                                releaseConnection(server, connectionSocket);
                                connectionSocket.close();
                                server.release();
                                break;
                            default:
                                break;
                        }
                    }
                }
            } catch (Exception e) {
                server.close();
                System.out.println("Server or Client shut down unexpectedly.");
            }
        }
    }
}


