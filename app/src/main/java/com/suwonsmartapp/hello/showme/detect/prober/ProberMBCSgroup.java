package com.suwonsmartapp.hello.showme.detect.prober;

public class ProberMBCSgroup extends ProberCharset {

    private ProbingState state;
    private ProberCharset[] probers;
    private boolean[] isActive;
    private int bestGuess;
    private int activeNum;

    public ProberMBCSgroup() {
        super();

        this.probers = new ProberCharset[7];
        this.isActive = new boolean[7];

        this.probers[0] = new ProberUTF8();
        this.probers[1] = new ProberSJIS();
        this.probers[2] = new ProberEUCJP();
        this.probers[3] = new ProberGB18030();
        this.probers[4] = new ProberEUCKR();
        this.probers[5] = new ProberCharsetBig5();
        this.probers[6] = new ProberEUCTW();

        reset();
    }

    @Override
    public String getCharSetName() {
        if (this.bestGuess == -1) {
            getConfidence();
            if (this.bestGuess == -1) {
                this.bestGuess = 0;
            }
        }
        return this.probers[this.bestGuess].getCharSetName();
    }

    @Override
    public float getConfidence() {
        float bestConf = 0.0f;
        float cf;

        if (this.state == ProbingState.FOUND_IT) {
            return 0.99f;
        } else if (this.state == ProbingState.NOT_ME) {
            return 0.01f;
        } else {
            for (int i = 0; i < probers.length; ++i) {
                if (!this.isActive[i]) {
                    continue;
                }

                cf = this.probers[i].getConfidence();
                if (bestConf < cf) {
                    bestConf = cf;
                    this.bestGuess = i;
                }
            }
        }

        return bestConf;
    }

    @Override
    public ProbingState getState() {
        return this.state;
    }

    @Override
    public ProbingState handleData(byte[] buf, int offset, int length) {
        ProbingState st;

        boolean keepNext = true;
        byte[] highbyteBuf = new byte[length];
        int highpos = 0;

        int maxPos = offset + length;
        for (int i = offset; i < maxPos; ++i) {
            if ((buf[i] & 0x80) != 0) {
                highbyteBuf[highpos++] = buf[i];
                keepNext = true;
            } else {
                //if previous is highbyte, keep this even it is a ASCII
                if (keepNext) {
                    highbyteBuf[highpos++] = buf[i];
                    keepNext = false;
                }
            }
        }

        for (int i = 0; i < this.probers.length; ++i) {
            if (!this.isActive[i]) {
                continue;
            }
            st = this.probers[i].handleData(highbyteBuf, 0, highpos);
            if (st == ProbingState.FOUND_IT) {
                this.bestGuess = i;
                this.state = ProbingState.FOUND_IT;
                break;
            } else if (st == ProbingState.NOT_ME) {
                this.isActive[i] = false;
                --this.activeNum;
                if (this.activeNum <= 0) {
                    this.state = ProbingState.NOT_ME;
                    break;
                }
            }
        }

        return this.state;
    }

    @Override
    public void reset() {
        this.activeNum = 0;
        for (int i = 0; i < this.probers.length; ++i) {
            this.probers[i].reset();
            this.isActive[i] = true;
            ++this.activeNum;
        }
        this.bestGuess = -1;
        this.state = ProbingState.DETECTING;
    }

    @Override
    public void setOption() {
    }
}