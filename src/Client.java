import java.io.*;
import java.net.*;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    public boolean fileRdy = false;//server的文件是否就绪

    protected void receive(Packet packet) {
        receivedPacket = packet;
    }

    protected byte[] getBytes(InputStream stream) throws Exception{
        int byteRead;
        ArrayList<Byte> list = new ArrayList<>();
        int quit = 0;
        while (quit < 4) {
            byteRead = stream.read();
//            System.out.println("read = " + byteRead);
            if ((byte) byteRead == '#') {
                quit++;
            }else {
                quit = 0;
            }
            list.add((byte) byteRead);
        }
//        System.out.println("size = " + list.size());
        byte[] buf = new byte[list.size() - 4];
        for (int i = 0; i < list.size() - 4; i++) {
//            System.out.println("i = " + i);
            buf[i] = list.get(i);
        }
        return buf;
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
//        System.out.println(fromClient.length);
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
//        System.out.println("gggg");
        byte[] data = Arrays.copyOfRange(fromClient, 24, fromClient.length);
        if (data.length > 0)
            p.setData(new Data(data));
        return p;
    }
    private void printSleep(int times, int slp) throws Exception{
        for (int i = 0; i < times; i++) {
            Thread.sleep(slp);
            System.out.print(".");
        }
        Thread.sleep(slp);
        System.out.println();
    }
    /**
     * Client握手建立连接
     * */
    protected void buildConnection(Socket target) throws Exception {
        //ServerSocket welcomeSocket = new ServerSocket(port);

        System.out.println("Trying to connect " + target.getInetAddress().toString().substring(1) + ":" + serverPort + "...");
        // 发送syn请求报文
        int seq = new Random().nextInt(123);// 该报文第一个字节id
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
        printSleep(5, 200);
        System.out.println("------------------------------first from client------------------------------");
        System.out.println();
        System.out.println(firstShakeHand);
        System.out.println("-----------------------------------------------------------------------------");
        send(target, firstShakeHand);
        // 接收Ack报文
//        Socket listenSocket = welcomeSocket.accept();
        System.out.println("welcome");
        InputStream bytesFromClient = target.getInputStream();
        //OutputStream bytesToClient = target.getOutputStream();
        byte[] buffer = getBytes(bytesFromClient);// buffer中存放client发来报文的字节形式
        Packet secondHandShake = buildPacket(buffer);// 组装报文
        receive(secondHandShake);// 收到client的第一次握手报文
//        System.out.println("client receive mss = " + secondHandShake.MSS);
        MSS = secondHandShake.MSS; //MSS大小统一，先统一为server的（发数据方）
        printSleep(5, 200);
        System.out.println("-----------------------------second from server------------------------------");
        System.out.println();
        print();// 回显报文
        System.out.println("-----------------------------------------------------------------------------");

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
        printSleep(5, 200);
        System.out.println("-----------------------------third from client-------------------------------");
        System.out.println();
        System.out.println(secondShakeHand);
        System.out.println("-----------------------------------------------------------------------------");
        send(target, secondShakeHand);
    }
    /**
     * Client挥手释放连接
     * */
    private static void releaseConnection(Client client, Socket connectingSocket) throws Exception {
        int ack = 0;
        int checkSum = 0;
        int seq = new Random().nextInt(123);// 随机
        InputStream bytesFromClient = connectingSocket.getInputStream();
        byte[] buffer = client.getBytes(bytesFromClient);
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
        buffer = client.getBytes(bytesFromClient);
        Packet secondShakeHand = client.buildPacket(buffer);
        client.receive(secondShakeHand);
        client.print();

        if (client.checkFIN(secondShakeHand)) {
            // client 进入 FIN-WAIT-2
            bytesFromClient = connectingSocket.getInputStream();
            buffer = client.getBytes(bytesFromClient);
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
        int segmentSize = window1.getSegmentSize(), size = window1.getSize();
        int windowBufferSize = size / segmentSize;//window一次可以放的报文段数

        //第一个报文特殊处理
        file.seek(filePointer);
        InputStream input = new FileInputStream(file.getFD());
        int seq = new Random().nextInt(123);// 该报文第一个字节id
        int tmp = seq;

        //step1: 用数据包填满window（需要检查是否可以装填）
        int wd = 1, ack = 0;
        while (filePointer < fileLen){
            System.out.println("*******************************第" + wd + "个窗口********************************");
            System.out.println();
            wd++;
            System.out.println("-----------------------------build packets------------------------------");
            if (window1.ifFinished()) {
                while (!window1.full()) {
                    if (filePointer >= fileLen) break;
                    file.seek(filePointer);
                    int cnt = 0;
                    ArrayList<Byte> list = new ArrayList<Byte>();
                    while (cnt < segmentSize && filePointer < fileLen) {
                        cnt++;
                        filePointer++;//更新指针
                        list.add((byte) input.read());
                    }
                    byte[] buffer = new byte[list.size()];
                    for (int i = 0; i < list.size(); i++)
                        buffer[i] = list.get(i);

                    byte[] id = Transformer.toBytes(seq, 4);
                    //ack在不知道应答的时候无法确认是多少
                    byte[] Ack = Transformer.toBytes(ack, 4);
                    byte[] spcBytes = new byte[2];
                    spcBytes[0] = host.receivedPacket.getSpcBytes()[0];
                    spcBytes[1] = (byte) (1 << 4);// ACK置1
                    int checkSum = 0;
                    Packet p = new Packet(
                            Transformer.toBytes(host.port, 2), host.receivedPacket.getSrc(), id, Ack,
                            spcBytes, Transformer.toBytes(window1.getSegmentSize(), 2), Transformer.toBytes(checkSum, 2),
                            host.receivedPacket.getUrgent(), host.receivedPacket.getOptions(),
                            host.receivedPacket.getAlign(), host.receivedPacket.MSS
                    );
                    p.setData(new Data(buffer));
                    window1.replace(p);
                    System.out.println(p);
                    seq += segmentSize;
                    //if (seq < 0) seq += (1 << 31);
                }
            }
            System.out.println("packet has been in window");
            System.out.println("------------------------------------------------------------------------");
            System.out.println();
            //step2: 处理应答、逐包发送
            int i = 0;
            seq = tmp;
            for (int j = 0; j < window1.getSize() / window1.getSegmentSize(); j++) {
                if (window1.packets[j] == null || window1.packets[j].isAck) continue;
                //发送
                window1.packets[j].setAck(Transformer.toBytes(ack, 4));
                window1.packets[j].setId(Transformer.toBytes(seq, 4));
                int possible = new Random().nextInt(1000);
                Packet p = window1.packets[j];
                if (possible < 100) {
                    if (possible < 25) {
                        //miss
                        p.setMessage(Error.MISS_MSG);
                    }else if (possible < 50) {
                        //shuffle
                        p.setMessage(Error.SHUFFLE_MSG);
                    }else if (possible < 75) {
                        //timeout
                        p.setMessage(Error.TIME_OUT);
                    }else {
                        //wrong
                        p.setMessage(Error.WRONG_MSG);
                    }
                    host.send(connectionSocket, p);
                    InputStream help = connectionSocket.getInputStream();
                    Packet buf = host.buildPacket(host.getBytes(help));
                }
                window1.packets[j].setMessage(Error.NO_ERROR);
                System.out.println("------start sending packet:------");
                System.out.println(window1.packets[j]);
                host.send(connectionSocket, window1.packets[j]);
                System.out.println("------------send done------------");
                //处理应答
                byte[] buffer;
                InputStream bytesFromServer = connectionSocket.getInputStream();// 获取字节流
                buffer = host.getBytes(bytesFromServer);// buffer中存放发来报文的字节形式
                System.out.println("receive reply");
                Packet reply = host.buildPacket(buffer);// 组装报文
                host.receive(reply);// 收到对方的reply
                ack = Transformer.toInteger(host.receivedPacket.getId()) + 1;
                seq = Transformer.toInteger(host.receivedPacket.getAck());
                if (host.receivedPacket != null) {
                    System.out.printf("第%d个应答: \n", ++i);
//                    host.printSleep();
                    host.print();// 回显报文
                    checkAndRenew(host.receivedPacket, window1);
                } else {
                    System.out.println("null reply");
                }
            }
            tmp = seq;
            System.out.println("************************************************************************");
        }
        Packet eof = new Packet();
        eof.setMessage(Controller.EOF);
        host.send(connectionSocket, eof);//告知client数据传输完毕（不需要应答）
    }

    /**
     * 检查应答报文，并更新windows窗口状态
     * @param packet 需要检查的报文
     * @param window 需要更新的窗口
     */
    private static void checkAndRenew(Packet packet,Window window){
        for (int i = 0; i < window.getSize() / window.getSegmentSize(); i++){
            if (Transformer.toInteger(window.packets[i].getAck()) == Transformer.toInteger(packet.getId()) && packet.ackValid()){
                window.packets[i].isAck = true;
                break;
            }
        }
    }

    /**
     * 对于收到的报文进行应答
     * @param srcPacket 收到的报文
     * @param connectionSocket 建立连接的服务端
     * */
    public void reply(Client client, Packet srcPacket, Socket connectionSocket) throws Exception{
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
                Transformer.toBytes(client.port, 2), srcPacket.getSrc(),
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
        String usr = "user";
        int portTmp = 64;
        if (argv.length != 1) {
            System.out.println("Parameter count error!");
            return;
        }
        try {
            portTmp = Integer.parseInt(argv[0]);
        }catch (Exception e) {
            System.out.println("Invalid port!");
            return;
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Client started!");
        while (true) {
            System.out.println("Enter a port to connect:");
            String serverPort = scanner.next();
            try {
                Client client = new Client("127.0.0.1", serverPort, String.valueOf(portTmp));
                InetAddress address = InetAddress.getByAddress(client.serverAddr);
                Socket socket = new Socket(address, client.serverPort);
                client.buildConnection(socket);
                System.out.println("Connection succeeded!!!");
                scanner.nextLine();//吃掉回车
                while (true) {
                    System.out.printf("%s> ", usr);
                    String command = scanner.nextLine();
                    String pattern1 = "set -(w|m) (\\d+)", pattern2 = "^get\\s+(.+)$";// (pathname)设置要传的文件
                    String pattern3 = "^hostname\\s+(.+)$"; // hostname (name)设置用户名
                    //文件路径就绪才可传输（fileRdy）
                    Pattern regex1 = Pattern.compile(pattern1), regex2 = Pattern.compile(pattern2), regex3 = Pattern.compile(pattern3);
                    Matcher matcher1 = regex1.matcher(command), matcher2 = regex2.matcher(command), matcher3 = regex3.matcher(command);
                    if (command.equals("quit") || command.equals("exit")) {
                        Packet rel = Controller.getCtrlPkt(0, "release");
                        client.send(socket, rel);
                        // TODO 模拟四次挥手过程
//                        releaseConnection(client, socket);
                        socket.close();
                        break;
                    }
                    else if (command.equals("check")) {
                        //查看当前报文
                        client.print();
                    }else if (matcher1.find()) {
                        if (command.charAt(5) == 'w') {
                            int window_size = cmd2number(matcher1);
                            System.out.print("setting server.");
                            client.printSleep(5, 200);
                            System.out.println("done!");
                            Packet p = Controller.getCtrlPkt(window_size, "window");
                            client.send(socket, p);
                        }else if (command.charAt(5) == 'm') {
                            int mss = cmd2number(matcher1);
                            System.out.print("setting server.");
                            client.printSleep(5, 200);
                            System.out.println("done!");
                            Packet p = Controller.getCtrlPkt(mss, "MSS");
                            client.send(socket, p);
                        }else {
                            System.out.printf("no command set -%s", matcher1.group(1));
                        }
                    }else if (matcher3.find()) {
                        //hostname
                        usr = matcher3.group(1);
                    }else if (matcher2.find()) {
                        //get file
                        String s = matcher2.group(1);
                        Packet ctrl = Controller.getCtrlPkt(s);
                        Controller.setPath(client, ctrl, socket);
                        client.sentPacket = null;
                        if (client.fileRdy) {
                            String storeFileName = UUID.randomUUID().toString();
//                            System.out.println(storeFileName);
                            File newfile = new File(storeFileName);
                            boolean flag = newfile.createNewFile();
                            FileOutputStream store = null;
                            if (flag) {
                                store = new FileOutputStream(storeFileName, true);
                            }
                            Packet p = Controller.getCtrlPkt(0, "get");
                            client.send(socket, p);//get报文的ACK视为0, 后续的都是1
                            while (true) {
                                InputStream fromServer = socket.getInputStream();
                                Packet segment = client.buildPacket(client.getBytes(fromServer));
                                int chc = Controller.check(segment);
                                if (chc == Controller.EOF) {
                                    client.printSleep(5, 200);
                                    System.out.println("send done!");
                                    break;
                                }
                                chc = Error.check(segment);
                                switch (chc) {
                                    case Error.MISS_MSG:
                                        client.printSleep(10, 400);
                                        System.out.println("################ 丢失 ################");
                                        System.out.println("-----------------重传-----------------");
                                        break;
                                    case Error.SHUFFLE_MSG:
                                        client.printSleep(5, 400);
                                        System.out.println("################ 乱序 ################");
                                        System.out.println("--------------按照序号重组--------------");
                                        break;
                                    case Error.TIME_OUT:
                                        client.printSleep(5, 1000);
                                        System.out.println("################# 超时 ################");
                                        System.out.println("------------已获得重新传输内容------------");
                                        break;
                                    case Error.WRONG_MSG:
                                        client.printSleep(5, 300);
                                        System.out.println("################ 出错 ################");
                                        System.out.println("-----------------重传-----------------");
                                        break;
                                    default: break;
                                }
                                if (chc != Error.NO_ERROR) {
                                    client.send(socket, new Packet());
                                    continue;
                                }
                                if (flag) {
                                    byte[] data = segment.getData().getData();
                                    store.write(data);
                                }
                                client.printSleep(5, 200);
                                System.out.println("------------------------receive data------------------------");
                                System.out.println();
                                System.out.println(segment);
                                System.out.println("------------------------------------------------------------");
                                client.reply(client, segment, socket);
                            }
                            if (flag) {
                                System.out.println("Data has been stored in file '" + storeFileName + "'.");
                                store.close();
                            }
                        }else {
                            System.out.println("No such file '" + s + "'");
                        }
                        client.fileRdy = false;
                    }else {
                        System.out.println("command '" + command + "' not found.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection failed. Request time out...");
            }
        }
    }
}
