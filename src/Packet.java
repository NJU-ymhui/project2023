import javax.xml.validation.SchemaFactory;
import java.util.*;

public class Packet {
    private Data data = null;

    public int headSize;
    public boolean isAck = false; // 该报文是否已被确认
    private byte[] srcPort = new byte[2];
    private byte[] destPort = new byte[2];
    private byte[] id = new byte[4];
    private byte[] ack = new byte[4];
    private byte[] spcBytes = new byte[2];//储存数据偏移（0~3）保留（4~9）URG(10)ACK(11)PSH(12)RST(13)SYN(14)FIN(15)
    private byte[] Window = new byte[2];
    private byte[] check = new byte[2];
    private byte[] urgent = new byte[2];
    public int MSS;
    private byte[] options;//可选项
    private byte[] align;//填充

    public Packet(
            byte[] src, byte[] dest, byte[] id, byte[] ack, byte[] spcBytes,
            byte[] Window, byte[] check, byte[] urgent, byte[] options, byte[] align, int MSS) {
        this.srcPort = src;
        this.destPort = dest;
        this.id = id;
        this.ack = ack;
        this.spcBytes = spcBytes;
        this.Window = Window;
        this.check = check;
        this.urgent = urgent;
        this.options = options;
        this.align = align;
        this.MSS = MSS;
        headSize = 24 + options.length + align.length;
        data = null;
        isAck = false;
    }
    public Packet() {
        this(
                new byte[2], new byte[2],
                new byte[4],
                new byte[4],
                new byte[2], new byte[2],
                new byte[2], new byte[2],
                new byte[]{}, new byte[]{},
                0
        );
    }
    /**
     * 将报文转化为字节流传输
     * @return 字节流
     * */
    public byte[] getBytes() {
        int dataLen = 0;
        if (data != null && data.getData() != null) dataLen = data.getData().length;
        byte[] res = new byte[24 + options.length + align.length + dataLen + 4];
        System.arraycopy(srcPort, 0, res, 0, 2);
        System.arraycopy(destPort,0, res, 2, 2);
        System.arraycopy(id, 0, res, 4, 4);
        System.arraycopy(ack, 0, res, 8, 4);
        System.arraycopy(spcBytes, 0, res, 12, 2);
        System.arraycopy(Window, 0, res, 14, 2);
        System.arraycopy(check, 0, res, 16, 2);
        System.arraycopy(urgent, 0, res, 18, 2);
        System.arraycopy(Transformer.toBytes(MSS, 4), 0, res, 20, 4);

        System.arraycopy(options, 0, res, 24, options.length);
        System.arraycopy(align, 0, res, 24 + options.length, align.length);

        if (dataLen > 0) {
            System.arraycopy(data.getData(), 0, res, 24 + options.length + align.length, dataLen);
        }
        res[res.length - 4] = (byte) '#';//'####'作为结束符
        res[res.length - 3] = (byte) '#';
        res[res.length - 2] = (byte) '#';
        res[res.length - 1] = (byte) '#';
        //7(4B) = \0\0\0\7
        return res;
    }
    public void setMessage(int msg) {
        switch (msg) {
            case Error.WRONG_MSG:
                spcBytes[0] = (byte) 0b00000001;
                break;
            case Error.MISS_MSG:
                spcBytes[0] = (byte) 0b00000100;
                break;
            case Error.SHUFFLE_MSG:
                spcBytes[0] = (byte) 0b00001000;
                break;
            case Error.TIME_OUT:
                spcBytes[0] = (byte) 0b00000010;
                break;
            case Controller.PATH_SET:
                spcBytes[0] = (byte) 0b00010000;
                break;
            case Controller.MSS_SET:
                spcBytes[0] = (byte) 0b00100000;
                break;
            case Controller.WINDOW_SET:
                spcBytes[0] = (byte) 0b01000000;
                break;
            case Controller.DATA_TRANSFER:
                spcBytes[0] = (byte) 0b10000000;
                break;
            case Controller.EOF:
                spcBytes[0] = (byte) 0b10000001;
                break;
            case Controller.RELEASE:
                spcBytes[0] = (byte) 0b11111111;
                break;
            default:
                spcBytes[0] = (byte) 0b00000000;
                break;
        }
    }
    public void setWindow(byte[] window) {
        this.Window = window;
    }
    public void setSrcPort(byte[] port) {
        srcPort = port;
    }
    public void setDestPort(byte[] port) {
        destPort = port;
    }
    public void setAck(byte[] ack) {
        this.ack = ack;
    }
    public void setId(byte[] seq) {
        id = seq;
    }
    public int getMSS() {
        return MSS;
    }
    public byte[] getSrc() {
        return srcPort;
    }
    public int getSrcPort() {
        return (int) srcPort[0] * 256 + (int) srcPort[1];
    }
    public byte[] getDest() {
        return destPort;
    }
    public int getDestPort() {
        return (int) destPort[0] * 256 + (int) destPort[1];
    }
    public byte[] getId() {
        return id;
    }
    private int getSeq() {
        return (id[0] << 24) + (id[1] << 16) + (id[2] << 8) + (id[3] & 0x00ff);
    }
    public byte[] getAck() {
        return ack;
    }
    private int getAckNum() {
        return (ack[0] << 24) + (ack[1] << 16) + (ack[2] << 8) + (ack[3] & 0x00ff);
    }
    public byte[] getSpcBytes() { return spcBytes; }
    /**
     * @return 获得offset，也是首部长度
     * **/

    public int getOffset() {
        int offset = 0;
        for (int i = 0; i < 4; i++) {
            offset += ((spcBytes[0] >> i) & 1) * (1 << i);
        }
        return offset;
    }
    public int getReserve() {
        return 0;
    }
    public byte[] getWindow() {
        return Window;
    }
    private int getWindowNum() {
        return (Window[0] * 256) + (Window[1] & 0xff);
    }
    public byte[] getCheck() {
        return check;
    }
    public byte[] getUrgent() {
        return urgent;
    }
    public byte[] getOptions() {
        return options;
    }
    public byte[] getAlign() {
        return align;
    }
    public void setData(Data data) {
        this.data = data;
    }
    public Data getData() {
        return data;
    }
    public boolean urgentValid() {
        return ((spcBytes[1] >> 5) & 1) == 1;
    }
    public boolean ackValid() {
        return ((spcBytes[1] >> 4) & 1) == 1;
    }
    public boolean pushNow() {
        return ((spcBytes[1] >> 3) & 1) == 1;
    }
    public boolean isReset() {
        return ((spcBytes[1] >> 2) & 1) == 1;
    }
    public boolean checkSYN() {
        return ((spcBytes[1] >> 1) & 1) == 1;
    }
    public boolean checkFIN() {
        return (spcBytes[1] & 1) == 1;
    }

    @Override
    public String toString() {
        return String.format("src:%d dest:%d SYN:%d FIN:%d ACK:%d seq:%d ack:%d window:%d data:%s\n",
                getSrcPort(),
                getDestPort(),
                checkSYN() ? 1 : 0,
                checkFIN() ? 1 : 0,
                ackValid() ? 1 : 0,
                getSeq(),
                getAckNum(),
                getWindowNum(),
                data == null ? " --No DATA--" : '\n' + data.toString());
    }
}