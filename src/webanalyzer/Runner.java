/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package webanalyzer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author michal
 */
class Runner implements Runnable {

    private Database database;

    // Pattern for recognizing a URL, based off RFC 3986
    private static final Pattern urlPattern = Pattern.compile(
            "(?:^|[\\W])((ht|f)tp(s?)://|www\\.)"
            + "(([\\w\\-]+\\.)+?([\\w\\-.~]+/?)*"
            + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    @Override
    public void run() {
        String page;

        while (true) { // run forever
            page = database.getPage();
            if (page == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                process(page);
            }
        }
    }

    Runner(Database database) {
        this.database = database;
    }

    private void process(String url) {
        URL obj;
        InputStream is = null;
        BufferedReader br;
        String line, poweredBy;

        try {
            obj = new URL(url);

            if (obj.getProtocol() == null || obj.getHost() == null) {
                database.updatePage(url, false, true);
                return;
            }

            URLConnection conn = obj.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            poweredBy = conn.getHeaderField("X-Powered-By");

            is = conn.getInputStream();
            br = new BufferedReader(new InputStreamReader(is));

            while ((line = br.readLine()) != null) {
                Matcher matcher = urlPattern.matcher(line);
                while (matcher.find()) {
                    int matchStart = matcher.start(1);
                    int matchEnd = matcher.end();
                    // now you have the offsets of a URL match
                    String newPage = line.substring(matchStart, matchEnd);
                    database.addPage(newPage);
                }
            }

            boolean useNette = (poweredBy != null) && poweredBy.equals("Nette Framework");

            System.out.print(useNette ? "Y" : ".");

            database.updatePage(url, useNette, false);
        } catch (Exception e) {
            // error, wrong url. Impossible to call
            database.updatePage(url, false, true);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException ioe) {
                // nothing to see here
            }
        }
    }
}
