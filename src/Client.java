import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;
import java.lang.Thread;
public class Client {
    // Hello^_^, I'm Pchan.

    public Packet receivedPacket = null;
    public Packet sentPacket = null;
    public int MSS = 1500;
    protected int port;
    protected Window window = new Window(); //创建一个窗口，维护这个window

    private byte[] serverAddr = new byte[4];
    private int serverPort;
    protected ServerSocket welcomeSocket;
    protected Socket connectionSocket = null;

    protected void receive(Packet packet) {
        receivedPacket = packet;
    }

    protected byte[] getBytes(InputStream stream, int size) throws Exception{
        int byteRead, len = 0;
        byte[] buffer = new byte[size];
//        System.out.println("size = " + size);
        while (len < size && (byteRead = stream.read()) >= 0) {
            buffer[len++] = (byte)byteRead;
//            System.out.printf("%d ", len);
        }
        return buffer;
    }

    protected boolean checkACK(Packet packet) { return packet.ackValid(); }
    protected boolean checkPSH(Packet packet) { return packet.pushNow(); }
    protected boolean checkRST(Packet packet) { return packet.isReset(); }
    protected boolean checkURG(Packet packet) { return packet.urgentValid(); }

    /**
     * 检查FIN位，判断是否挥手释放连接
     * @param packet
     * @return boolean
     * */
    protected boolean checkFIN(Packet packet) {
        return packet.checkFIN();
    }
    /**
     * 检查SYN位，判断是否握手建立连接
     * @param packet
     * @return boolean
     * */
    protected boolean checkSYN(Packet packet) {
        return packet.checkSYN();
    }
    /**
     * 该方法发送报文
     * @param connectionSocket: 与客户端建立连结的服务端
     * @param packet 报文
     * */
    protected void send(Socket connectionSocket, Packet packet) throws IOException {
        OutputStream outToServer = connectionSocket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServer);
        if (packet == null) {
            System.out.println("error: nullPointerException in packet: NULL");
            return;
        }
        out.write(packet.getBytes());
    }
    /***
     *回显当前报文内容，即显示收到的信息
     *回显格式：src:... dest:... id:... ack:... window:... data:...
     */
    protected void print() {
        System.out.println(receivedPacket);
    }
    /**
     * 把收到的字节流组装成报文, 字节流中一定包含首部，可能包含数据
     * @param fromClient: 从客户端收到的字节流
     * @return Packet: 组装后的报文
     * */
    protected Packet buildPacket(byte[] fromClient) {
        int headSize = ((int) fromClient[12] >> 4) * 4;
        Packet p =  new Packet(Arrays.copyOfRange(fromClient, 0, 2),
                Arrays.copyOfRange(fromClient, 2, 4),
                Arrays.copyOfRange(fromClient, 4, 8),
                Arrays.copyOfRange(fromClient, 8, 12),
                Arrays.copyOfRange(fromClient, 12, 14),
                Arrays.copyOfRange(fromClient, 14, 16),
                Arrays.copyOfRange(fromClient, 16, 18),
                Arrays.copyOfRange(fromClient, 18, 20),
                new byte[]{},
                new byte[]{},
                Transformer.toInteger(Arrays.copyOfRange(fromClient, 20, 24)));
        byte[] data = Arrays.copyOfRange(fromClient, 24, fromClient.length);
        if (data.length > 0)
            p.setData(new Data(data));
        return p;
    }
    private void printSleep() throws Exception{
        for (int i = 0; i < 5; i++) {
            Thread.sleep(200);
            System.out.print(".");
        }
        Thread.sleep(200);
        System.out.println();
    }
    /**
     * Client握手建立连接
     * */
    protected void buildConnection(Socket target) throws Exception {
        //ServerSocket welcomeSocket = new ServerSocket(port);

        System.out.println("Trying to connect " + target.getInetAddress().toString().substring(1) + ":" + serverPort + "...");
        // 发送syn请求报文
        int seq = new Random().nextInt(123456789);// 该报文第一个字节id
        byte[] id = Transformer.toBytes(seq, 4);
        int ack = 0;
        byte[] Ack = Transformer.toBytes(ack, 4);
        byte[] spcBytes = new byte[2];
        spcBytes[1] = 2;// SYN置1
        int checkSum = 0;
        Packet firstShakeHand = new Packet(
                new byte[]{(byte) (port / 256), (byte) (port % 256)},
                new byte[]{(byte) (serverPort / 256), (byte) (serverPort % 256)},
                id,
                Ack,
                spcBytes,
                new byte[]{(byte) (100 / 256), (byte) (100 % 256)},
                Transformer.toBytes(checkSum, 2),
                new byte[]{0, 0},
                new byte[]{},
                new byte[]{},
                MSS);
        System.out.println("开始握手:");
        System.out.println("first from client:");
        printSleep();
        System.out.println(firstShakeHand);
        send(target, firstShakeHand);
        // 接收Ack报文
//        Socket listenSocket = welcomeSocket.accept();
        System.out.println("welcome");
        InputStream bytesFromClient = target.getInputStream();
        //OutputStream bytesToClient = target.getOutputStream();
        byte[] buffer = getBytes(bytesFromClient, 24);// buffer中存放client发来报文的字节形式
        Packet secondHandShake = buildPacket(buffer);// 组装报文
        receive(secondHandShake);// 收到client的第一次握手报文
        System.out.println("client receive mss = " + secondHandShake.MSS);
        MSS = secondHandShake.MSS; //MSS大小统一，先统一为server的（发数据方）
        System.out.println("second from server: ");
        printSleep();
        print();// 回显报文

        // 发送Ack报文
        seq++;
        id = Transformer.toBytes(seq, 4);
        ack = Transformer.toInteger(receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
        Ack = Transformer.toBytes(ack, 4);
        spcBytes = new byte[2];
        spcBytes[0] = receivedPacket.getSpcBytes()[0];
        spcBytes[1] = (byte) (receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1
        checkSum = 0;
        Packet secondShakeHand = new Packet(
                receivedPacket.getDest(), receivedPacket.getSrc(), id, Ack,
                spcBytes, receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                receivedPacket.getUrgent(), receivedPacket.getOptions(),
                receivedPacket.getAlign(), receivedPacket.MSS
        );
        System.out.println("third from client:");
        printSleep();
        System.out.println(secondShakeHand);
        send(target, secondShakeHand);
    }
    /**
     * Client挥手释放连接
     * */
    private static void releaseConnection(Client client, Socket connectingSocket) throws Exception {
        int ack = 0;
        int checkSum = 0;
        int seq = new Random().nextInt(1234567890);// 随机
        InputStream bytesFromClient = connectingSocket.getInputStream();
        byte[] buffer = client.getBytes(bytesFromClient, 24);
        byte[] id = Transformer.toBytes(seq, 4);
        byte[] Ack = Transformer.toBytes(ack, 4);
        byte[] spcBytes = new byte[2];
        spcBytes[1] = (byte) (spcBytes[1] | 0b00000001);// FIN置1
        Packet firstHandShake = new Packet(
                client.receivedPacket.getDest(), client.receivedPacket.getSrc(), id, Ack,
                spcBytes, client.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                client.receivedPacket.getUrgent(), client.receivedPacket.getOptions(),
                client.receivedPacket.getAlign(), client.receivedPacket.MSS);
        client.send(connectingSocket, firstHandShake);
        // client 进入 FIN-WAIT-1
        bytesFromClient = connectingSocket.getInputStream();
        buffer = client.getBytes(bytesFromClient, 24);
        Packet secondShakeHand = client.buildPacket(buffer);
        client.receive(secondShakeHand);
        client.print();

        if (client.checkFIN(secondShakeHand)) {
            // client 进入 FIN-WAIT-2
            bytesFromClient = connectingSocket.getInputStream();
            buffer = client.getBytes(bytesFromClient, 24);
            Packet thirdShakeHand = client.buildPacket(buffer);
            client.receive(thirdShakeHand);
            client.print();

            if (client.checkFIN(thirdShakeHand)) {
                seq = Transformer.toInteger(client.receivedPacket.getAck()) + 1;
                ack = Transformer.toInteger(client.receivedPacket.getId()) + 1;
                Packet fourthHandShake = new Packet(
                        client.receivedPacket.getDest(), client.receivedPacket.getSrc(), id, Ack,
                        spcBytes, client.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                        client.receivedPacket.getUrgent(), client.receivedPacket.getOptions(),
                        client.receivedPacket.getAlign(), client.receivedPacket.MSS);
                client.send(connectingSocket, fourthHandShake);
                // 进入TIME-WAIT超时等待2MSL
                System.out.println("Connection released.");
                connectingSocket.close();
            } else {
                System.out.println("Failed to release connection.");
            } // 不符合挥手规范，抛出异常
            // }
        } else {
            System.out.println("Failed to release connection.");
        } // 不符合挥手规范，抛出异常
    }
    /**
     * 数据传输
     * @param host 发送方
     * */
    protected static void dataTransfer(Client host, Socket connectionSocket, String path) throws Exception {
        Window window1 = host.window;
        RandomAccessFile file = new RandomAccessFile(path, "r");
        long fileLen = file.length();
        long filePointer = file.getFilePointer();

        //第一个报文特殊处理
        file.seek(filePointer);
        InputStream input = new FileInputStream(file.getFD());
        int seq = new Random().nextInt(123456789);// 该报文第一个字节id
        int tmp = seq;
        byte[] buffer;

        //step1: 用数据包填满window（需要检查是否可以装填）
        System.out.println("-----------------------------build packets------------------------------");
        if (window1.ifFinished() && filePointer < fileLen){
//            System.out.println("fileLen = " + fileLen);
//            System.out.println("filePointer = " + filePointer);
            while (!window1.full()){
                if (filePointer >= fileLen) break;
                buffer = host.getBytes(input, host.MSS);// buffer中存放User数据
//                System.out.println("MSS = " + host.MSS);
//                System.out.println(buffer.length);
                filePointer += buffer.length;//更新指针
//                System.out.println("filePointer = " + filePointer);
                byte[] id = Transformer.toBytes(seq, 4);
                int ack = 0;//ack在不知道应答的时候无法确认
                byte[] Ack = Transformer.toBytes(ack, 4);
                byte[] spcBytes = new byte[2];
                spcBytes[0] = host.receivedPacket.getSpcBytes()[0];
                spcBytes[1] = (byte) (1 << 4);// ACK置1
                int checkSum = 0;
                Packet p = new Packet(
                        host.receivedPacket.getDest(), host.receivedPacket.getSrc(), id, Ack,
                        spcBytes, host.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                        host.receivedPacket.getUrgent(), host.receivedPacket.getOptions(),
                        host.receivedPacket.getAlign(), host.receivedPacket.MSS
                );
                p.setData(new Data(buffer));
                window1.replace(p);
                System.out.println(p);
                seq += host.MSS;
                //if (seq < 0) seq += (1 << 31);
            }
        }
        System.out.println("packet has been in window");
        System.out.println("------------------------------------------------------------------------");
        //step2: 处理应答、逐包发送
        int i = 0, ack = 0;
        seq = tmp;
        for (Packet item : window1.packets){
            if (item == null || item.isAck) continue;
            //发送
            item.setAck(Transformer.toBytes(ack, 4));
            item.setId(Transformer.toBytes(seq, 4));
            System.out.println("start sending packet:");
            System.out.println(item);
            host.send(connectionSocket, item);
            System.out.println("-----send done-----");
            //处理应答
            InputStream bytesFromServer = connectionSocket.getInputStream();// 获取字节流
            buffer = host.getBytes(bytesFromServer, 24);// buffer中存放发来报文的字节形式
            System.out.println("receive reply");
            Packet reply = host.buildPacket(buffer);// 组装报文
            host.receive(reply);// 收到对方的reply
            ack = Transformer.toInteger(host.receivedPacket.getId()) + host.MSS;
            seq = Transformer.toInteger(host.receivedPacket.getAck());
            if (host.receivedPacket != null){
                System.out.printf("%d reply: \n", i++);
                host.printSleep();
                host.print();// 回显报文
                checkAndRenew(host.receivedPacket,window1);
            }else {
                System.out.println("null reply");
            }
        }
    }

    /**
     * 检查应答报文，并更新windows窗口状态
     * @param packet 需要检查的报文
     * @param window 需要更新的窗口
     */
    private static void checkAndRenew(Packet packet,Window window){
        for (Packet p : window.packets){
            if (p.getAck() == packet.getId() && packet.ackValid()){
                p.isAck = true;
            }
        }

        //是否改变windows窗口大小
        //window.size = Transformer.toInteger(packet.getWindow());
    }

    /**
     * 对于收到的报文进行应答
     * @param srcPacket 收到的报文
     * @param connectionSocket 建立连接的服务端
     * */
    public void reply(Packet srcPacket, Socket connectionSocket) throws Exception{
        if (srcPacket == null) {
            System.out.println("src = null!!!!!!!!!!!!!!!!!!!");
            return;
        }
        if (sentPacket != null) {
            if (Transformer.toInteger(sentPacket.getAck()) != Transformer.toInteger(srcPacket.getId()))
                System.out.println("wrong sequence");
        }
        byte[] seq = Transformer.toBytes(Transformer.toInteger(srcPacket.getAck()), 4);
        int len = 1;
        if (srcPacket.getData() != null) {
            len = srcPacket.getData().getData().length;
        }
        byte[] ack = Transformer.toBytes(Transformer.toInteger(srcPacket.getId()) + len, 4);
        byte[] spc = srcPacket.getSpcBytes();
        spc[1] |= (byte) 0b0010000;//ACK=1
        Packet p = new Packet(
                srcPacket.getDest(), srcPacket.getSrc(),
                seq,
                ack,
                spc,
                srcPacket.getWindow(),
                srcPacket.getCheck(),
                new byte[]{0, 0},
                new byte[]{},
                new byte[]{},
                MSS
        );
        sentPacket = p;
        send(connectionSocket, p);
    }
    public Client(String addr, String serverPort, String port) {
        String addrPattern = "(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)";
        Pattern r = Pattern.compile(addrPattern);
        Matcher matcher = r.matcher(addr);
        if (matcher.find()) {
            serverAddr[0] = Byte.parseByte(matcher.group(1));
            serverAddr[1] = Byte.parseByte(matcher.group(2));
            serverAddr[2] = Byte.parseByte(matcher.group(3));
            serverAddr[3] = Byte.parseByte(matcher.group(4));
        } else {
            System.out.println("Wrong IP Address!");
        }
        this.serverPort = Byte.parseByte(serverPort);
        this.port = Byte.parseByte(port);
    }

    public Client() {

    }
    /** 可能存在的指令(暂定)：
     * send (filename)
     * get (filename)
     */
    private static int cmd2number(Matcher matcher) {
        return Integer.parseInt(matcher.group(2));
    }

    public static void main(String[] argv) throws Exception{
        Scanner scanner = new Scanner(System.in);
        System.out.println("Client started!");
        while (true) {
            System.out.println("Enter a port to connect:");
            String serverPort = scanner.next();
            try {
                Client client = new Client("127.0.0.1", serverPort, "64");
                InetAddress address = InetAddress.getByAddress(client.serverAddr);
                Socket socket = new Socket(address, client.serverPort);
                //模拟三次握手过程
//                        client = new Client("127.0.0.1", serverPort, "64");
//                        address = InetAddress.getByAddress(client.serverAddr);
//                        socket = new Socket(address, client.serverPort);
                client.buildConnection(socket);
                System.out.println("Connection succeeded!!!");

                InputStream bytesFromServer = socket.getInputStream();
                byte[] b = client.getBytes(bytesFromServer, 24 + client.MSS);
                Packet rcv = client.buildPacket(b);
                client.receive(rcv);
                System.out.println("-------------------------------------------------");
                System.out.println("receive data segment:");
                client.printSleep();
                client.print();
                client.reply(client.receivedPacket, socket);
//                System.out.println("reply done");

                while (true) {
                    String command = scanner.nextLine();
                    String pattern = "set -(w|p|m) (\\d+)";
                    Pattern regex = Pattern.compile(pattern);
                    Matcher matcher = regex.matcher(command);
                    if (command.equals("quit") || command.equals("exit")) {
                        // TODO 模拟四次挥手过程
                        //releaseConnection(client);
                        socket.close();
                        break;
                    }
                    else if (command.equals("check")) {
                        //查看当前报文
                        client.print();
                    }else if (matcher.find()) {
                        if (command.charAt(5) == 'w') {
                            System.out.println(cmd2number(matcher));
                        }else if (command.charAt(5) == 'm') {
                            System.out.println(cmd2number(matcher));
                        }else if (command.charAt(5) == 'p') {
                            System.out.println(cmd2number(matcher));
                        }else {
                            System.out.printf("no command set -%s", matcher.group(1));
                        }
                    }else if (command.equals("continue") || command.equals("c")) {
                        b = client.getBytes(bytesFromServer, 24 + client.MSS);
                        rcv = client.buildPacket(b);
                        client.receive(rcv);
                        client.printSleep();
                        client.print();
                        client.reply(client.receivedPacket, socket);
                    }
                    // TODO 处理各种指令
                }
//                socket.close();
            } catch (IOException e) {
                System.out.println("Connection failed. Request time out...");
            }
        }
    }
}
