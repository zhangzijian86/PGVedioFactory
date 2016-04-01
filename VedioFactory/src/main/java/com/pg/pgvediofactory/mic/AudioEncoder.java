package com.pg.pgvediofactory.mic;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoder {

    private MediaCodec mediaCodec;
    private String mediaType = "OMX.google.aac.encoder";

    ByteBuffer[] inputBuffers = null;
    ByteBuffer[] outputBuffers = null;

    // "OMX.qcom.audio.decoder.aac";
    // "audio/mp4a-latm";

    public AudioEncoder() {
        try {
            mediaCodec = MediaCodec.createByCodecName(mediaType);
        }catch (Exception e) {
            e.printStackTrace();
        }
        final int kSampleRates[] = { 8000, 11025, 22050, 44100, 48000 };
        final int kBitRates[] = { 64000,96000,128000 };
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm", kSampleRates[3], 2);
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, kBitRates[1]);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);// It will
        mediaCodec.configure(mediaFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        inputBuffers = mediaCodec.getInputBuffers();
        outputBuffers = mediaCodec.getOutputBuffers();
    }

    public void close() {
        try {
            mediaCodec.stop();
            mediaCodec.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // called AudioRecord's read
    public void offerEncoder(byte[] input, byte[] output) {
        Log.e("AudioEncoder", input.length + " is coming");
        int pos = 0;
        int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            inputBuffer.clear();
            inputBuffer.put(input);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        // //trying to add a ADTS
        while (outputBufferIndex >= 0) {
            int outBitsSize = bufferInfo.size;
            int outPacketSize = outBitsSize + 7; // 7 is ADTS size
            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];

            outputBuffer.position(bufferInfo.offset);
            outputBuffer.limit(bufferInfo.offset + outBitsSize);

            byte[] outData = new byte[outPacketSize];
            addADTStoPacket(outData, outPacketSize);

            outputBuffer.get(outData, 7, outBitsSize);
            outputBuffer.position(bufferInfo.offset);

            System.arraycopy(outData, 0, output, pos, outData.length);
            pos += outData.length;

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        }
    }

    /**
     * Add ADTS header at the beginning of each and every AAC packet. This is
     * needed as MediaCodec encoder generates a packet of raw AAC data.
     *
     * Note the packetLen must count in the ADTS header itself.
     **/
    public void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        // 39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = 4; // 44.1KHz
        int chanCfg = 2; // CPE

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}