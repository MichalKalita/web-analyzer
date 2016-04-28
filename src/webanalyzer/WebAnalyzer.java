/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package webanalyzer;

/**
 * @author michal
 */
public class WebAnalyzer {

    private Database database;

    public static void main(String[] args) {
        int threads = 10;
        boolean export = false;

        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--threads":
                        if (i + 1 < args.length) {
                            threads = Integer.parseInt(args[i + 1]);
                            i++; // skip next
                        } else {
                            System.out.println("Missing count of threads");
                        }
                        break;
                    case "--export":
                        export = true;
                        break;
                    default:
                        System.out.println("Not recognized parameter " + args[i]);
                        break;
                }
            }
        }

        if (export) {
            new Database().export();
        } else {
            new WebAnalyzer(threads);
        }
    }

    public WebAnalyzer(int countOfThreads) {
        database = new Database();

        Thread report = new Thread() {
            public void run() {
                try {
                    while (true) {
                        database.generateReport();
                        Thread.sleep(30 * 1000); // sleep 30 seconds and make report
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        report.setDaemon(true);
        report.start();

        Thread[] threads = new Thread[countOfThreads];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runner(database));
            threads[i].start();
        }
    }

}
