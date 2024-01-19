public class Controller {
    //spc = 0b00010000: send path
    //spc = 0b00100000: send mss
    //spc = 0b01000000: send window-size
    //spc = 0b00000000: trivial packet
    //spc = 0b10000000: data transfer
    public final static int DATA_PACKET = 0;
    public final static int PATH_SET = 16;
    public final static int MSS_SET = 32;
    public final static int WINDOW_SET = 64;
    public final static int DATA_TRANSFER = 128;
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
        }
        return p;
    }
    public static int check(Packet packet) {
        //调用方针对返回结果得知控制的类型，从而做出相应行为
        if (packet == null) return Error.check(null);
        byte spc = packet.getSpcBytes()[0];
        if (spc == (byte) 0b00000000) return DATA_PACKET;
        else if (spc == (byte) 0b00010000) return PATH_SET;
        else if (spc == (byte) 0b00100000) return MSS_SET;
        else if (spc == (byte) 0b01000000) return WINDOW_SET;
        else if (spc == (byte) 0b10000000) return DATA_TRANSFER;
        else return Error.check(packet);
    }
}
