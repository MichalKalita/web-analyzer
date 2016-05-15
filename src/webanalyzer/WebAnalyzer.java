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

    public static boolean debug = false;

    public static void main(String[] args) {
        int threads = 10;
        boolean export = false, report = false;

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
                    case "--report":
                        report = true;
                        break;
                    case "--debug":
                        debug = true;
                        break;
                    default:
                        System.out.println("Not recognized parameter " + args[i]);
                        break;
                }
            }
        }

        if (export) {
            new Database().export();
        } else if(report) {
            new Database().generateReport();
        } else {
            System.out.println(threads + " threads");
            new WebAnalyzer(threads);
        }
    }

    public WebAnalyzer(int countOfThreads) {
        Database database = new Database();

        Thread[] threads = new Thread[countOfThreads];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new Runner(database));
            threads[i].start();
        }
    }

}
