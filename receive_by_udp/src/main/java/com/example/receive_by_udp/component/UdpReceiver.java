package com.example.receive_by_udp.component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
@Component
public class UdpReceiver implements CommandLineRunner {
  private static final int PORT = 30000;
  private static final int RTP_HEADER_SIZE = 12;
  private int sequenceNumber = 0;
  private int timestamp = 0;
  private final int ssrc = new Random().nextInt();
  /**
   * 30000 포트로 UDP 패킷 수신
   * waveData 배열에 저장
   *
   * 해당 데이터를 RTP 패킷으로 변환하는 함수(createRtpPacket) 로 넘겨줌
   */
  @Override
  public void run(String... args) throws Exception {
    DatagramSocket udpSocket = new DatagramSocket(PORT);
    byte[] buffer = new byte[4096];
    // 애플리케이션 종료시 소켓 닫기.
    Runtime.getRuntime().addShutdownHook(new Thread(() ->{
      if(udpSocket != null && !udpSocket.isClosed()){
        udpSocket.close();
        System.out.println("UDP socket closed");
      }
    }));
    while(true) {
      DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
      udpSocket.receive(udpPacket);
      System.out.println("Received packet from "+ udpPacket.getAddress() + ":" + udpPacket.getPort());
      byte[] waveData = udpPacket.getData();
      int wavLength = udpPacket.getLength();
      byte[] rtpPacket = createRtpPacket(waveData, wavLength);
      printRtpPacket(rtpPacket);
    }
  }
  /**
   * [RTP 패킷 생성]
   * 0x80 =>  RTP 버전 2 를 의미
   * Payload Type => 96을 사용하여 동적 페이로드 형식으로 지정
   * Sequence Number => 패킷 순서를 유지하기 위해 증하가며, 두 바이트로 표현
   * Timestamp => 샘플링 시간 간격에 따라 증가하여 타이밍을 유지함.
   * SSRC => RTP 스트림을 식별하는 고유 IDx
   *
   * 페이로드 삽입 => System.arraycopy 를 사용하여, wavData(WAV 데이터)를 RTP 패킷의 헤더 뒤에
   * 복사하여 RTP 패킷의 페이로드로 생성.
   *
   * @param wavData
   * @param wavLength
   * @return
   */
  private byte[] createRtpPacket(byte[] wavData, int wavLength){
    byte[] rtpPacket = new byte[RTP_HEADER_SIZE + wavLength];
    rtpPacket[0] = (byte) 0x80;
    rtpPacket[1] = (byte) 96;
    rtpPacket[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
    rtpPacket[3] = (byte) (sequenceNumber & 0xFF);
    ByteBuffer.wrap(rtpPacket, 4, 4).putInt(timestamp);
    ByteBuffer.wrap(rtpPacket, 8, 4).putInt(ssrc);
    System.arraycopy(wavData, 0, rtpPacket, RTP_HEADER_SIZE, wavLength);
    sequenceNumber++;
    timestamp += 160;
    return rtpPacket;
  }
  /**
   * [RTP 패킷 출력 및 해석]
   *  header 와 payload
   *  Version, Payload Type, Sequence Number, Timestamp, SSRC를 해석하여 콘솔에 출력
   *  데시벨 계산 호출 => 페이로드 데이터를 데시벨로 계산하기 위해 calculateDecibel 메서드를 호출합니다.
   * @param rtpPacket
   */
  private void printRtpPacket(byte[] rtpPacket){
    byte[] header = Arrays.copyOfRange(rtpPacket, 0, RTP_HEADER_SIZE);
    byte[] payload = Arrays.copyOfRange(rtpPacket, RTP_HEADER_SIZE, rtpPacket.length);
    System.out.println("RTP Header: " + Arrays.toString(header));
    System.out.println("RTP payload: " + Arrays.toString(payload));
    int version = (header[0] >> 6) & 0x03;
    int payloadType= header[1] & 0x7F;
    int sequenceNumber = ((header[2] & 0xFF) << 8 | (header[3] & 0xFF));
    int timestamp = ByteBuffer.wrap(header, 4, 4).getInt();
    int ssrc = ByteBuffer.wrap(header, 8, 4).getInt();
    System.out.println("Version: " + version);
    System.out.println("Payload Type: " + payloadType);
    System.out.println("Sequence Number: " + sequenceNumber);
    System.out.println("Timestamp: " + timestamp);
    System.out.println("SSRC: " + ssrc);
    calculateDecibel(payload);
  }
  /**
   * [데시벨 계산]
   * 페이로드 샘플링 => 페이로드를 2바이트 (16비트) 샘플 단위로 처리하여 RMS 값을 계산.
   * RMS 계산 => 샘플의 제곱값을 환산하여 RMS 를 계산
   * 데시벨 변환 => RMS 를 데시벨로 변환하여 출력
   * @param payload
   */
  private void calculateDecibel(byte[] payload) {
    int sampleCount = payload.length / 2;  // 오디오 샘플 개수 계산
    double sumOfSquares = 0.0;             // 샘플 값들의 제곱합을 누적하는 변수 => RMS 계산시 사용
    for (int i = 0; i <= payload.length - 2; i += 2) { // 길이를 초과하지 않도록 수정
        // payload 에서 2바이트씩 읽어 샘플 추출
        // 리틀 엔디안 형식으로 signed 16-bit 정수(short)로 변환.
        short sample = ByteBuffer.wrap(payload, i, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
        // 추출한 샘플 값을 제곱하여 sumOfSquares 에 더해줌.
        // 제곱값을 누적하여 모든 샘플의 제곱합을 계산.
        sumOfSquares += sample * sample;
    }
    if (sampleCount > 0) {
        // RMS 계산 => 평균 제곱근 계산
        double rms = Math.sqrt(sumOfSquares / sampleCount);
        // 데시벨 변환
        double db = 20 * Math.log10(rms);
        System.out.println("Calculated Decibel Level: " + db + " dB");
    } else {
        System.out.println("No valid audio samples in payload.");
    }
  }
}