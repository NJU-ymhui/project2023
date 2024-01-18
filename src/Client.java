import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.Random;
import java.util.regex.Pattern;

import javax.xml.transform.Transformer;

public class Client {
    // Hello^_^, I'm Pchan.

    public Packet receivedPacket = null;
    public int MSS = 1500;
    protected int port;
    protected Window window = new Window(); // 创建一个窗口，维护这个window

    private byte[] serverAddr = new byte[4];
    private int serverPort;

    protected void receive(Packet packet) {
        receivedPacket = packet;
    }

    protected byte[] getBytes(InputStream stream, int size) throws Exception {
        int byteRead, len = 0;
        byte[] buffer = new byte[size];
        while ((byteRead = stream.read()) != -1 && len < size) {
            buffer[len++] = (byte) byteRead;
        }
        return buffer;
    }

    protected boolean checkACK(Packet packet) {
        return packet.ackValid();
    }

    protected boolean checkPSH(Packet packet) {
        return packet.pushNow();
    }

    protected boolean checkRST(Packet packet) {
        return packet.isReset();
    }

    protected boolean checkURG(Packet packet) {
        return packet.urgentValid();
    }

    /**
     * 检查FIN位，判断是否挥手释放连接
     * 
     * @param packet
     * @return boolean
     */
    protected boolean checkFIN(Packet packet) {
        return packet.checkFIN();
    }

    /**
     * 检查SYN位，判断是否握手建立连接
     * 
     * @param packet
     * @return boolean
     */
    protected boolean checkSYN(Packet packet) {
        return packet.checkSYN();
    }

    /**
     * 该方法发送报文
     * 
     * @param connectionSocket: 与客户端建立连结的服务端
     * @param packet            报文
     */
    protected void send(Socket connectionSocket, Packet packet) throws IOException {
        OutputStream outToServer = connectionSocket.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServer);
        out.write(packet.getBytes());
    }

    /***
     * 回显当前报文内容，即显示收到的信息
     * 回显格式：src:... dest:... id:... ack:... window:... data:...
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
     * 
     * @param fromClient: 从客户端收到的字节流
     * @return Packet: 组装后的报文
     */
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
                new byte[] {},
                MSS);
    }

    /**
     * Client握手建立连接
     */
    private static void buildConnection(Client client) {
        // TODO
    }

    /**
     * Client挥手释放连接
     */
    private static void releaseConnection(Client client, Socket connectingSocket) throws Exception {
        int ack = 0;
        int checkSum = 0;
        int seq = new Random().nextInt(1234567890);// 随机
        InputStream bytesFromClient = connectingSocket.getInputStream();
        byte[] buffer = client.getBytes(bytesFromClient, 20);
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
        buffer = client.getBytes(bytesFromClient, 20);
        Packet secondShakeHand = client.buildPacket(buffer);
        client.receive(secondShakeHand);
        client.print();

        if (client.checkFIN(secondShakeHand)) {
            client.connected = false;
            // client 进入 FIN-WAIT-2
            bytesFromClient = connectingSocket.getInputStream();
            buffer = client.getBytes(bytesFromClient, 20);
            packet thirdShakeHand = client.buildPacket(buffer);
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
     * 
     * @param host 发送方
     */
    protected static void dataTransfer(Client host) {
        // TODO
        // 使用并维护window进行此项任务
        // step1: 用数据包填满window（需要检查是否可以装填）
        // step2: 逐包发送，并对应答进行检查，更新window状态
        // step3: 如果window所有数据包均被确认，发送下一窗口
        // 循环...
    }

    /**
     * 对于收到的报文进行应答
     * 
     * @param srcPacket        收到的报文
     * @param connectionSocket 建立连接的服务端
     */
    public void reply(Packet srcPacket, Socket connectionSocket) {
        // TODO
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

    /**
     * 可能存在的指令(暂定)：
     * send (filename)
     * get (filename)
     */
    protected String[] commands = new String[] { "send (.*)", "get (.*)" };

    protected boolean run(String command) {
        for (String s : commands) {

        }
        return false;
    }

    // TODO 其余需要实现的但该代码中未考虑到的部分
    public static void main(String[] argv) throws Exception {
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
