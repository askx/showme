package com.suwonsmartapp.hello.showme.detect.prober;

import com.suwonsmartapp.hello.showme.detect.character.Constants;
import com.suwonsmartapp.hello.showme.detect.context.ContextEUCJP;
import com.suwonsmartapp.hello.showme.detect.distribute.DistributeJISEUCJP;
import com.suwonsmartapp.hello.showme.detect.state.StateMachineCoding;
import com.suwonsmartapp.hello.showme.detect.state.StateMachineEUCJP;
import com.suwonsmartapp.hello.showme.detect.state.StateMachine;

public class ProberEUCJP extends ProberCharset {

    private StateMachineCoding codingSM;
    private ProbingState state;

    private ContextEUCJP contextAnalyzer;
    private DistributeJISEUCJP distributionAnalyzer;

    private byte[] lastChar;

    private static final StateMachine STATE_MACHINE = new StateMachineEUCJP();

    public ProberEUCJP() {
        super();
        this.codingSM = new StateMachineCoding(STATE_MACHINE);
        this.contextAnalyzer = new ContextEUCJP();
        this.distributionAnalyzer = new DistributeJISEUCJP();
        this.lastChar = new byte[2];
        reset();
    }

    @Override
    public String getCharSetName() {
        return Constants.CHARSET_EUC_JP;
    }

    @Override
    public float getConfidence() {
        float contextCf = this.contextAnalyzer.getConfidence();
        float distribCf = this.distributionAnalyzer.getConfidence();

        return Math.max(contextCf, distribCf);
    }

    @Override
    public ProbingState getState() {
        return this.state;
    }

    @Override
    public ProbingState handleData(byte[] buf, int offset, int length) {
        int codingState;

        int maxPos = offset + length;
        for (int i = offset; i < maxPos; ++i) {
            codingState = this.codingSM.nextState(buf[i]);
            if (codingState == StateMachine.ERROR) {
                this.state = ProbingState.NOT_ME;
                break;
            }
            if (codingState == StateMachine.ITSME) {
                this.state = ProbingState.FOUND_IT;
                break;
            }
            if (codingState == StateMachine.START) {
                int charLen = this.codingSM.getCurrentCharLen();

                if (i == offset) {
                    this.lastChar[1] = buf[offset];
                    this.contextAnalyzer.handleOneChar(this.lastChar, 0, charLen);
                    this.distributionAnalyzer.handleOneChar(this.lastChar, 0, charLen);
                } else {
                    this.contextAnalyzer.handleOneChar(buf, i - 1, charLen);
                    this.distributionAnalyzer.handleOneChar(buf, i - 1, charLen);
                }
            }
        }

        this.lastChar[0] = buf[maxPos - 1];

        if (this.state == ProbingState.DETECTING) {
            if (this.contextAnalyzer.gotEnoughData() && getConfidence() > SHORTCUT_THRESHOLD) {
                this.state = ProbingState.FOUND_IT;
            }
        }

        return this.state;
    }

    @Override
    public void reset() {
        this.codingSM.reset();
        this.state = ProbingState.DETECTING;
        this.contextAnalyzer.reset();
        this.distributionAnalyzer.reset();
        java.util.Arrays.fill(this.lastChar, (byte) 0);
    }

    @Override
    public void setOption() {
    }
}