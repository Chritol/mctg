package bif3.tolan.swe1.mctg;

import bif3.tolan.swe1.mctg.constants.ServerConstants;
import bif3.tolan.swe1.mctg.httpserver.HttpServer;
import bif3.tolan.swe1.mctg.persistence.PersistenceManager;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        PersistenceManager dbConnection = new PersistenceManager();

        HttpServer server = new HttpServer(ServerConstants.DEFAULT_SERVER_PORT);
        server.initializeWorkers(dbConnection);

        Thread httpServerThread = new Thread(server);
        httpServerThread.start();

        Scanner scanner = new Scanner(System.in);
        boolean exitApplication = false;

        while (!exitApplication) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("stop")) {
                    httpServerThread.interrupt();
                    System.out.println("----------------------------------------------");
                    System.out.println("Server stopped");
                    System.out.println("----------------------------------------------");
                } else if (input.equalsIgnoreCase("exit")) {
                    if (!httpServerThread.isInterrupted()) {
                        System.out.println("----------------------------------------------");
                        System.out.println("Http server thread still running. Please close it with 'stop' first!");
                        System.out.println("----------------------------------------------");
                    } else {
                        exitApplication = true;
                    }
                }
            }
        }

        System.exit(0);
    }
}
