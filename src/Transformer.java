

public class Transformer {
    public static int toInteger(byte[] bytes) {
        int ans = 0;
        for (int i = 0; i < bytes.length; i++) ans += bytes[bytes.length - 1 - i] * (1 << (i * 8));
        return ans;
    }
    public static byte[] toBytes(int number, int bytes) {
        byte[] res = new byte[bytes];
        res[bytes - 1] = (byte) (number % (1 << 7));
        for (int i = 0; i < bytes - 1; i++) {
            res[i] = (byte) (number % (1 << (8 * (bytes - i) - 1)) / (1 << (8 * (bytes - i - 1) - 1)));
        }
        return res;
    }

    /**
     *  计算checkSum
     * @param data :根据数据计算检验和
     * @return :报文中的检验和字段
     * */
    public static byte[] calculateCheckSum(Data data) {
        //TODO
        return null;
    }
}
