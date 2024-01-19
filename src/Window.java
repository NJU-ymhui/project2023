import java.util.Arrays;

public class Window {
    public final int DEFAULT_SIZE = 100;
    public final int DEFAULT_SEGMENT = 20;
    private int size = DEFAULT_SIZE; // Byte
    private int segmentSize = DEFAULT_SEGMENT; // 每个数据段的大小，认为就是MSS的值
    public Packet[] packets;
    public void initPackets() {
        packets = new Packet[size / segmentSize]; // 先设置好size再初始化packets
        Arrays.fill(packets, null);
    }
    public void setSize(int sz) {
        size = sz;
        initPackets();
    }
    public int getSize() {
        return size;
    }
    public int getSegmentSize() {
        return segmentSize;
    }
    public void setSegmentSize(int sz) {
        segmentSize = size;
        initPackets();
    }
    /**
     * 检查window数据包是否发送完毕
     * */
    public boolean ifFinished() {
        //用于判断是否可以装填数据包
        //null等效于ACK
        for (Packet packet : packets) if (packet != null && !packet.isAck) return false;
        return true;
    }
    public boolean full() {
        for (Packet p : packets) {
            if (p == null || p.isAck) return false;
        }
        return true;
    }

    /**
     * 寻找packets中已被应答的报文并替换为参数报文packet
     * @param packet 替换的报文
     * */
    public void replace(Packet packet) {
        for (int i = 0; i < packets.length; i++) {
            if (packets[i] == null || packets[i].isAck) {
                packets[i] = packet;
                break;
            }
        }
    }
    //todo other possible methods
}

