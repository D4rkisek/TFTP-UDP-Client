package client;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPUtil {
    // Constants for TFTP opcodes and error codes
    public static final short OP_RRQ = 1;
    public static final short OP_WRQ = 2;
    public static final short OP_DATA = 3;
    public static final short OP_ACK = 4;
    public static final short OP_ERROR = 5;

    public static final short ERR_FILE_NOT_FOUND = 1;

    // Helper methods for reading and writing data packets, as well as error handling
    public static DatagramPacket createReadRequestPacket(InetAddress serverAddress, int port, String fileName) {
        return createRequestPacket(serverAddress, port, fileName, OP_RRQ);
    }

    public static DatagramPacket createWriteRequestPacket(InetAddress serverAddress, int port, String fileName) {
        return createRequestPacket(serverAddress, port, fileName, OP_WRQ);
    }

    private static DatagramPacket createRequestPacket(InetAddress serverAddress, int port, String fileName, short opcode) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        try {
            dataOutputStream.writeShort(opcode);
            dataOutputStream.writeBytes(fileName);
            dataOutputStream.writeByte(0);
            dataOutputStream.writeBytes("octet");
            dataOutputStream.writeByte(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] requestData = byteArrayOutputStream.toByteArray();
        return new DatagramPacket(requestData, requestData.length, serverAddress, port);
    }

    public static DatagramPacket createDataPacket(InetAddress serverAddress, int port, short blockNumber, byte[] data, int dataLength) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        try {
            dataOutputStream.writeShort(OP_DATA);
            dataOutputStream.writeShort(blockNumber);
            dataOutputStream.write(data, 0, dataLength);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] dataPacketData = byteArrayOutputStream.toByteArray();
        return new DatagramPacket(dataPacketData, dataPacketData.length, serverAddress, port);
    }

    public static DatagramPacket createAckPacket(InetAddress serverAddress, int port, short blockNumber) {
        byte[] ackData = new byte[4];
        ByteBuffer.wrap(ackData).putShort(OP_ACK).putShort(blockNumber);
        return new DatagramPacket(ackData, ackData.length, serverAddress, port);
    }

    public static DatagramPacket createErrorPacket(InetAddress serverAddress, int port, short errorCode, String errorMessage) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        try {
            dataOutputStream.writeShort(OP_ERROR);
            dataOutputStream.writeShort(errorCode);
            dataOutputStream.writeBytes(errorMessage);
            dataOutputStream.writeByte(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] errorData = byteArrayOutputStream.toByteArray();
        return new DatagramPacket(errorData, errorData.length, serverAddress, port);
    }

    public static void sendFile(DatagramSocket socket, InetAddress serverAddress, int port, String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + fileName);
        }

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            short blockNumber = 1;
            byte[] buffer = new byte[512];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                DatagramPacket dataPacket = createDataPacket(serverAddress, port, blockNumber, buffer, bytesRead);
                socket.send(dataPacket);
                System.out.println("Sent data packet with block number: " + blockNumber);

                DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
                socket.receive(ackPacket);
                short ackBlockNumber = ByteBuffer.wrap(ackPacket.getData()).getShort(2);
                System.out.println("Received ACK packet with block number: " + ackBlockNumber);

                if (ackBlockNumber != blockNumber) {
                    throw new IOException("Invalid ACK received");
                }

                blockNumber++;
            }
        }
    }

    public static void receiveFile(DatagramSocket socket, InetAddress serverAddress, int port, String fileName) throws IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            short expectedBlockNumber = 1;

            while (true) {
                DatagramPacket dataPacket = new DatagramPacket(new byte[516], 516);
                socket.receive(dataPacket);
                System.out.println("Received data packet with block number: " + expectedBlockNumber);

                short opcode = ByteBuffer.wrap(dataPacket.getData()).getShort();
                short blockNumber = ByteBuffer.wrap(dataPacket.getData()).getShort(2);

                if (opcode != OP_DATA || blockNumber != expectedBlockNumber) {
                    throw new IOException("Invalid data packet received");
                }

                int dataLength = dataPacket.getLength() - 4;
                fileOutputStream.write(dataPacket.getData(), 4, dataLength);

                DatagramPacket ackPacket = createAckPacket(serverAddress, port, blockNumber);
                socket.send(ackPacket);
                System.out.println("Sent ACK packet with block number: " + blockNumber);

                expectedBlockNumber++;

                if (dataLength < 512) {
                    break;
                }
            }
        }
    }

    public static short getOpcode(DatagramPacket packet) {
        byte[] data = packet.getData();
        return ByteBuffer.wrap(data).getShort();
    }

    public static String getFileName(DatagramPacket packet) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        StringBuilder fileName = new StringBuilder();
        for (int i = 2; i < length; i++) {
            char c = (char) data[i];
            if (c == 0) {
                break;
            }
            fileName.append(c);
        }

        return fileName.toString();
    }
}