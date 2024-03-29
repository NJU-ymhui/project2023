

import java.io.InputStream;
import java.net.Socket;

public class Controller {
    //spc = 0b00010000: send path
    //spc = 0b00100000: send mss
    //spc = 0b01000000: send window-size
    //spc = 0b00000000: trivial packet
    //spc = 0b10000000: data transfer
    //spc = 0b11111111: release
    //spc = 0b10000001: 一个文件传完了，用于server告知client
    //spc = 0b10000010: check window&mss size
    public final static int DATA_PACKET = 0;
    public final static int PATH_SET = 16;
    public final static int MSS_SET = 32;
    public final static int WINDOW_SET = 64;
    public final static int DATA_TRANSFER = 128;
    public final static int RELEASE = 255;
    public final static int EOF = 129;
    public final static int CHECK = 130;
    /**
     * 获取控制报文
     * */
    public static Packet getCtrlPkt(String path) {
        Packet p = new Packet(
                new byte[2],
                new byte[2],
                new byte[4],
                new byte[4],
                new byte[]{(byte) 0b00000000, 0b00000000},
                new byte[2],
                new byte[2],
                new byte[2],
                new byte[]{},
                new byte[]{},
                0
        );
        byte[] buf = path.getBytes();
        p.setData(new Data(buf));
        p.setMessage(PATH_SET);
        return p;
    }
    /**
     * 设置服务端客户端希望接受文件的路径
     * */
    public static void setPath(Client client, Packet ctrl, Socket socket) throws Exception{
        client.send(socket, ctrl);
        InputStream fromServer = socket.getInputStream();
        Packet rcv = client.buildPacket(client.getBytes(fromServer));
//        System.out.println(new String(rcv.getData().getData()));
        client.fileRdy = Error.check(rcv) == Error.NO_ERROR;
    }
    public static Packet getCtrlPkt(int mssOrWindowSize, String choice) {
        Packet p = new Packet(
                new byte[2],
                new byte[2],
                new byte[4],
                new byte[4],
                new byte[]{(byte) 0b00000000, 0b00000000},
                new byte[2],
                new byte[2],
                new byte[2],
                new byte[]{},
                new byte[]{},
                0
        );
        p.setData(new Data(String.valueOf(mssOrWindowSize).getBytes()));
        if (choice.equals("MSS")) {
            p.setMessage(MSS_SET);
        }else if (choice.equals("window")) {
            p.setMessage(WINDOW_SET);
        }else if (choice.equals("get")) {
            p.setMessage(DATA_TRANSFER);
        }else if (choice.equals("release")) {
            p.setMessage(RELEASE);
        }else if (choice.equals("check")) {
            p.setMessage(CHECK);
        }
        return p;
    }
    /**
     * 检查当前报文类型
     * */
    public static int check(Packet packet) {
        //调用方针对返回结果得知控制的类型，从而做出相应行为
        if (packet == null) return Error.check(null);
        byte spc = packet.getSpcBytes()[0];
        if (spc == (byte) 0b00000000) return DATA_PACKET;
        else if (spc == (byte) 0b00010000) return PATH_SET;
        else if (spc == (byte) 0b00100000) return MSS_SET;
        else if (spc == (byte) 0b01000000) return WINDOW_SET;
        else if (spc == (byte) 0b10000000) return DATA_TRANSFER;
        else if (spc == (byte) 0b11111111) return RELEASE;
        else if (spc == (byte) 0b10000001) return EOF;
        else if (spc == (byte) 0b10000010) return CHECK;
        else return Error.check(packet);
    }
    public static void printHelp() {
        System.out.println("|----------------------------------------------------------------------------|");
        System.out.println("|hostname [usr-name]: set your client's name.                                |");
        System.out.println("|set:                                                                        |");
        System.out.println("|   -w [size]: set window size.                                              |");
        System.out.println("|   -m [mss]: set MSS.                                                       |");
        System.out.println("|get [filename]: get data transfer from server.                              |");
        System.out.println("|display: make packet transferring visible.                                  |");
        System.out.println("|close: make packet transferring invisible.                                  |");
        System.out.println("|check: check window&mss size.                                               |");
        System.out.println("|quit / exit: quit client.                                                   |");
        System.out.println("|----------------------------------------------------------------------------|");
    }
    /**
     * 打印报文的十六进制格式
     * */
    public static void printHex(Packet p) {
        byte[] buf = p.getBytes();
        int perLine = 0;
        for (int i = 0; i < buf.length; i++) {
            String hex1 = String.format("%02X", (buf[i] & 0xFF00) >> 8);
            String hex2 = String.format("%02X", buf[i] & 0xFF);
            System.out.printf("%s %s ", hex1, hex2);
            perLine++;
            if (perLine == 4 || i == buf.length - 1) {
                perLine = 0;
                System.out.println();
            }
        }
        System.out.println();
    }
}
