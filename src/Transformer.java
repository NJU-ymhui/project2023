

public class Transformer {
    public static int toInteger(byte[] bytes) {
        int ans = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[bytes.length - 1 - i];
            ans += ((int) b & 0x00ff) << (i * 8);
        }
        return ans;
    }
//    public static void main(String[] s) {
//        byte[] b = new byte[]{(byte) 0b00000001, 0b00110011};
//        System.out.println(toInteger(toBytes(toInteger(b), 2)));
//    }

    public static byte[] toBytes(int number, int bytes) {
        byte[] res = new byte[bytes];
//        res[bytes - 1] = (byte) (number % (1 << 7));
//        for (int i = 0; i < bytes - 1; i++) {
//            res[i] = (byte) (number % (1 << (8 * (bytes - i) - 1)) / (1 << (8 * (bytes - i - 1) - 1)));
//        }
        for (int i = bytes - 1; i >= 0; i--) {
            int tmp = number % (1 << 8);
            number /= 1 << 8;
            int j = 0;
            while (j < 8) {
                if (tmp % 2 == 1) {
                    res[i] = (byte) (res[i] | (1 << j));
                }
                j++;
                tmp /= 2;
            }
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
