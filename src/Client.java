import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Client {
    // Hello^_^, I'm Pchan.

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
            buffer[len++] = (byte)byteRead;
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
        out.write(packet.getBytes());
    }
    /***
     *回显当前报文内容，即显示收到的信息
     *回显格式：src:... dest:... id:... ack:... window:... data:...
     */
    protected void print() {
        System.out.printf("src:%d dest:%d id:%s ack:%s window:%s data:%s\n",
                receivedPacket.getSrcPort(),
                receivedPacket.getDestPort(),
                Arrays.toString(receivedPacket.getId()),
                Arrays.toString(receivedPacket.getBytes()),
                Arrays.toString(receivedPacket.getWindow()),
                Arrays.toString(receivedPacket.getData().getData()));
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
    private static void buildConnection(Client client) {
        //TODO
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
    protected static void dataTransfer(Client host) throws Exception {
        //TODO
        //使用并维护window进行此项任务
        //step1: 用数据包填满window（需要检查是否可以装填）
        //step2: 逐包发送，并对应答进行检查，更新window状态
        //step3: 如果window所有数据包均被确认，发送下一窗口
        //循环...


        Window window1 = host.window;
        Socket socket = new Socket(InetAddress.getByAddress(host.serverAddr), host.port);//todo:设置全局socket

        while(true){
            //step1: 用数据包填满window（需要检查是否可以装填）
            if (window1.ifFinished()){
                String url = "https://github.com/NJU-ymhui/project2023/blob/1dec49a989881b523d0e1956a5692cb82c84a1c3/src/testfile";
                FileInputStream fileInputStream = new FileInputStream(url);
                BufferedInputStream input = new BufferedInputStream(fileInputStream);

                int seq = new Random().nextInt(1234567890);// 该报文第一个字节id
                for (Packet item : window1.packets){
                    byte[] buffer = host.getBytes(input, host.MSS);// buffer中存放User数据
                    byte[] id = Transformer.toBytes(seq, 4);
                    int ack = Transformer.toInteger(host.receivedPacket.getId()) + 1;// 该报文ack应为上一个Server报文id + 1
                    byte[] Ack = Transformer.toBytes(ack, 4);
                    byte[] spcBytes = new byte[2];
                    spcBytes[0] = host.receivedPacket.getSpcBytes()[0];
                    spcBytes[1] = (byte) (host.receivedPacket.getSpcBytes()[1] | (1 << 4));// ACK置1
                    int checkSum = 0;//todo 计算checksum
                    Packet p = new Packet(
                            host.receivedPacket.getDest(), host.receivedPacket.getSrc(), id, Ack,
                            spcBytes, host.receivedPacket.getWindow(), Transformer.toBytes(checkSum, 2),
                            host.receivedPacket.getUrgent(), host.receivedPacket.getOptions(),
                            host.receivedPacket.getAlign(), host.receivedPacket.MSS
                    );
                    p.setData(new Data(buffer));
                    window1.replace(p);
                    seq++;
                }
            }

            //step2: 处理应答、逐包发送 todo：窗口存在未确定的判定以及处理情况
            for (Packet item : window1.packets){
                //处理来自Server的应答
                InputStream bytesFromServer = socket.getInputStream();// 获取Server的字节流
                byte[] buffer = host.getBytes(bytesFromServer, host.MSS);// buffer中存放Server发来报文的字节形式
                Packet reply = host.buildPacket(buffer);// 组装报文
                host.receive(reply);// 收到server的reply
                if (host.receivedPacket != null){
                    host.print();// 回显报文
                    checkAndRenew(host.receivedPacket,window1);
                }

                //发送
                OutputStream outToServer = socket.getOutputStream();
                DataOutputStream out = new DataOutputStream(outToServer);
                out.write(item.getBytes());
            }

            if(window1.ifFinished()) break;

        }

    }

    /**
     * 检查应答报文，并更新windows窗口状态
     * @param packet 需要检查的报文
     * @param window 需要更新的窗口
     */
    private static void checkAndRenew(Packet packet,Window window){
        for (Packet p : window.packets){
            if (p.getId() == packet.getAck() && packet.ackValid()){
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
    }

    public Client() {

    }


    /** 可能存在的指令(暂定)：
     * send (filename)
     * get (filename)
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
