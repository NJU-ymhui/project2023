public final class Error {
    //考虑到packet里的spcbytes只有spcbytes[1]用了，所以用spcbytes[0]代表出错情况和控制类型
    //类似故障字：0b00000000正常，0b00000001出错, 0b00000010超时, 0b00000100丢失, 0b00001000乱序
    //至多出现一种问题
    //在packet里已经实现好了相应的辅助函数（设置packet用的，这里应该用不到）
    public final static int NO_ERROR = 0;
    public final static int WRONG_MSG = 1;//出错
    public final static int TIME_OUT = 2;//超时
    public final static int MISS_MSG = 4;//丢失
    public final static int SHUFFLE_MSG = 8;//乱序
    /**
     * 判断是否出错/出错类型
     * @param rcvPacket 收到的报文，检查是否出错/错误类型
     * */
    public static int check(Packet rcvPacket) {
        if (rcvPacket == null) return TIME_OUT;
        byte spc = rcvPacket.getSpcBytes()[0];
        if (spc == (byte) 0b00000001)
            return WRONG_MSG;
        else if (spc == (byte) 0b00000010)
            return TIME_OUT;
        else if (spc == (byte) 0b00000100)
            return MISS_MSG;
        else if (spc == (byte) 0b00001000)
            return SHUFFLE_MSG;
        return NO_ERROR;
    }
}
