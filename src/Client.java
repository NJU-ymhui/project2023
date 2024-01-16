import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {
    public Packet receivedPacket = null;
    public int MSS = 1500;
    protected int port;
    protected Window window = new Window(); //创建一个窗口，维护这个window

    private byte[] serverAddr = new byte[4];
    private int serverPort;

    protected void receive(Packet packet) {
        receivedPacket = packet;
    }

    protected byte[] getBytes(InputStream stream, int size) throws Exception{
        int byteRead, len = 0;
        byte[] buffer = new byte[size];
        while ((byteRead = stream.read()) != -1 && len < size) {
            System.out.println(len);
            buffer[len++] = (byte)byteRead;
        }
        System.out.print("Over");
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
        out.write(packet.getBytes());
    }
    /***
     *回显当前报文内容，即显示收到的信息
     *回显格式：src:... dest:... id:... ack:... window:... data:...
     */
    protected void print() {
        System.out.print(".");
        System.out.printf(receivedPacket.toString());
    }
    /**
     * 把收到的字节流组装成报文, 字节流中一定包含首部，可能包含数据
     * @param fromClient: 从客户端收到的字节流
     * @return Packet: 组装后的报文
     * */
    protected Packet buildPacket(byte[] fromClient) {
        int headSize = ((int) fromClient[12] >> 4) * 4;
        return new Packet(Arrays.copyOfRange(fromClient, 0, 2),
                Arrays.copyOfRange(fromClient, 2, 4),
                Arrays.copyOfRange(fromClient, 4, 8),
                Arrays.copyOfRange(fromClient, 8, 12),
                Arrays.copyOfRange(fromClient, 12, 14),
                Arrays.copyOfRange(fromClient, 14, 16),
                Arrays.copyOfRange(fromClient, 16, 18),
                Arrays.copyOfRange(fromClient, 18, 20),
                Arrays.copyOfRange(fromClient, 20, fromClient.length),
                new byte[]{},
                MSS);
    }
    /**
     * Client握手建立连接
     * */
    protected void buildConnection(Socket target) throws Exception {
        ServerSocket welcomeSocket = new ServerSocket(port);

        System.out.println("Trying to connect " + target.getInetAddress().toString().substring(1) + ":" + serverPort + "...");
        // 发送syn请求报文
        int seq = new Random().nextInt(1234567890);// 该报文第一个字节id
        byte[] id = Transformer.toBytes(seq, 4);
        int ack = 0;
        byte[] Ack = Transformer.toBytes(ack, 4);
        byte[] spcBytes = new byte[2];
        spcBytes[1] = 2;// SYN置1
        int checkSum = 0;// todo calculate checkSum
        Packet firstShakeHand = new Packet(
                new byte[]{(byte) (port / 256), (byte) (port % 256)},
                new byte[]{(byte) (serverPort / 256), (byte) (serverPort % 256)},
                id,
                Ack,
                spcBytes,
                new byte[]{(byte) (MSS / 256), (byte) (MSS % 256)},
                Transformer.toBytes(checkSum, 2),
                new byte[]{0, 0},
                new byte[]{},
                new byte[]{},
                MSS);
        System.out.println("发送第一次握手SYN报文:");
        System.out.println(firstShakeHand);
        send(target, firstShakeHand);

        // 接收Ack报文
        Socket listenSocket = welcomeSocket.accept();
        System.out.println("welcome");
        InputStream bytesFromClient = listenSocket.getInputStream();
        OutputStream bytesToClient = listenSocket.getOutputStream();
        byte[] buffer = getBytes(bytesFromClient, 20);// buffer中存放client发来报文的字节形式
        Packet secondHandShake = buildPacket(buffer);// 组装报文
        receive(secondHandShake);// 收到client的第一次握手报文
        print();// 回显报文

        // 发送Ack报文
        seq++;
        id = Transformer.toBytes(seq, 4);
        ack = Transformer.toInteger(receivedPacket.getId()) + 1;// 该报文ack应为上一个报文id + 1
        Ack = Transformer.toBytes(ack, 4);
        spcBytes = new byte[2];
        spcBytes[0] = receivedPacket.getSpcBytes()[0];
        spcBytes[1] = (byte) (receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1
        checkSum = 0;// todo calculate checkSum
        Packet secondShakeHand = new Packet(
                receivedPacket.getDest(), receivedPacket.getSrc(), id, Ack,
                spcBytes, receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                receivedPacket.getUrgent(), receivedPacket.getOptions(),
                receivedPacket.getAlign(), receivedPacket.MSS
        );
        send(listenSocket, secondShakeHand);// 将报文发送给client，完成应答
    }
    /**
     * Client挥手释放连接
     * */
    private static void releaseConnection(Client client) {
        //TODO
    }
    /**
     * 数据传输
     * @param host 发送方
     * */
    protected static void dataTransfer(Client host) {
        //TODO
        //使用并维护window进行此项任务
        //step1: 用数据包填满window（需要检查是否可以装填）
        //step2: 逐包发送，并对应答进行检查，更新window状态
        //step3: 如果window所有数据包均被确认，发送下一窗口
        //循环...
    }
    /**
     * 对于收到的报文进行应答
     * @param srcPacket 收到的报文
     * @param connectionSocket 建立连接的服务端
     * */
    public void reply(Packet srcPacket, Socket connectionSocket) {
        //TODO
    }
    public Client(String addr, String port) {
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
        serverPort = Byte.parseByte(port);
        this.port = 100; // 设置默认从该窗口发送报文，可以通过指令修改
    }

    public Client() {

    }


    /** 可能存在的指令(暂定)：
     * 发送文件 send (filename)
     * 下载文件 get (filename)
     * 配置客户端： set
     *   设置发送端口 -p (portNumber)
     */
    protected String[] commands = new String[]{"send (.*)", "get (.*)"};
    protected boolean run(String command) {
        for (String s : commands) {

        }
        return false;
    }


    //TODO 其余需要实现的但该代码中未考虑到的部分
    public static void main(String[] argv) throws Exception{
        Scanner scanner = new Scanner(System.in);
        System.out.println("Client started!");
        while (true) {
            System.out.println("Enter a port to connect:");
            String port = scanner.next();
            try {
                Client client = new Client("127.0.0.1", port);
                InetAddress address = InetAddress.getByAddress(client.serverAddr);
                Socket socket = new Socket(address, client.serverPort);
                client.buildConnection(socket);
                // TODO 模拟三次握手过程
                System.out.println("Connection succeeded.");

                while (true) {
                    String command = scanner.nextLine();
                    if (command.equals("quit") || command.equals("exit")) {
                        // TODO 模拟四次挥手过程
                        break;
                    }
                    // TODO 处理各种指令
                }

                socket.close();
            } catch (IOException e) {
                System.out.println("Connection failed. Request time out...");
            }
        }
    }
}
