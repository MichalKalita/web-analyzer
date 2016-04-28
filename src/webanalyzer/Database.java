/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package webanalyzer;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.*;
import java.util.Iterator;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.sqlite.core.Codes.SQLITE_CONSTRAINT;

/**
 * @author michal
 */
class Database {

    private Connection c;

    private ConcurrentLinkedQueue getPageCache;

    private String dbFile = "web-analyzer.db";

    private boolean canStop = false;

    Database() {
        File file = new File(dbFile);
        boolean installed = file.exists();

        try {
            Class.forName("org.sqlite.JDBC");
            this.c = DriverManager.getConnection("jdbc:sqlite:" + dbFile);

        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }

        this.getPageCache = new ConcurrentLinkedQueue();

        if (installed) {
            System.out.println("Database is already installed");
        } else {
            System.out.println("Installing database ...");
            install();
            getPageCache.add(new Url("http://nette.org"));
            getPageCache.add(new Url("http://lydragos.cz"));
            getPageCache.add(new Url("https://gist.githubusercontent.com/Myiyk/7589213/raw/fcbff4625313e9c4f224177ecf02a27d745c788c/Soupis%20webů%20s%20Nette%20Framework"));
        }
        System.out.println("Database connected");
    }

    private void install() {
        Statement stmt;

        try {
            stmt = c.createStatement();
            String sql = "CREATE TABLE \"links\" (\n"
                    + "  \"from\" integer NOT NULL,\n"
                    + "  \"to\" integer NOT NULL,\n"
                    + "  FOREIGN KEY (\"from\", \"to\") REFERENCES \"pages\" (\"id\", \"id\") ON DELETE CASCADE ON UPDATE CASCADE\n"
                    + ");\n"
                    + "CREATE TABLE \"pages\" (\n"
                    + "  \"id\" integer NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
                    + "  \"secured\" integer NOT NULL,\n"
                    + "  \"url\" text NOT NULL,\n"
                    + "  \"isSubdomain\" integer NOT NULL,\n"
                    + "  \"useNette\" integer NULL,\n"
                    + "  \"error\" integer NULL,\n"
                    + "  \"dateAdded\" integer NOT NULL,\n"
                    + "  \"dateComputed\" integer NULL\n"
                    + ");\n"
                    + "CREATE UNIQUE INDEX \"pages_secured_domain\" ON \"pages\" (\"secured\", \"url\");";
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void addPage(String url) {
        PreparedStatement stmt = null;
        boolean isSubdomain;
        try {
            URI uri = new URI(url);

            String scheme = uri.getScheme();
            boolean secured;

            if (scheme == null) {
                return;
            }
            switch (scheme) {
                case "https":
                    secured = true;
                    break;
                case "http":
                    secured = false;
                    break;
                default:
                    return;
            }

            url = uri.getHost();
            if (url == null) {
                return;
            }
            String domain = url.substring(url.lastIndexOf('.', url.lastIndexOf('.') - 1) + 1);
            if (url.equals(domain)) { // address is domain, not subdomain
                isSubdomain = false;
            } else {
                // subdomain is www, it is domain
                if (url.substring(0, url.length() - domain.length()).equals("www.")) {
                    isSubdomain = false;
                } else { // subdomain isnt www, it is subdomain
                    isSubdomain = true;
                }
            }

            String sql = "INSERT INTO pages (secured, url, isSubdomain, dateAdded) "
                    + "VALUES (?, ?, ?, ?);";
            stmt = c.prepareStatement(sql);
            stmt.setBoolean(1, secured);
            stmt.setString(2, url);
            stmt.setBoolean(3, isSubdomain);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (URISyntaxException ex) {
//            System.err.println("Spatna adresa " + url);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != SQLITE_CONSTRAINT) {
                Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
            }
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    void updatePage(String url, boolean useNette, boolean error) {
        PreparedStatement stmt = null;
        try {
            URI uri = new URI(url);

            String scheme = uri.getScheme();
            boolean secured;

            switch (scheme) {
                case "https":
                    secured = true;
                    break;
                case "http":
                    secured = false;
                    break;
                default:
                    return;
            }

            url = uri.getHost();

            String sql = "UPDATE pages SET "
                    + "useNette = ?, "
                    + "error = ?, "
                    + "dateComputed = ? "
                    + "WHERE secured = ? AND url = ?;";
            stmt = c.prepareStatement(sql);
            stmt.setBoolean(1, useNette);
            stmt.setBoolean(2, error);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setBoolean(4, secured);
            stmt.setString(5, url);
            stmt.executeUpdate();

        } catch (URISyntaxException | SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Return url for testing
     *
     * @return
     */
    synchronized String getPage() {
        int count;
        int limit = 1000;
        if (getPageCache.size() <= 500) {
            count = loadPages(limit, "cz", true); // zaklad jsou cz domeny

            if (count < limit) { // sk domeny
                count += loadPages(limit - count, "sk", true);
            }

            if (count < limit) { // eu domeny
                count += loadPages(limit - count, "eu", true);
            }

            if (count < limit) { // zbytek světa
                count += loadPages(limit - count, null, false);
            }
            if (count == 0) {
                System.err.println("\nCan stop is TRUE !!!\n");
                this.canStop = true;
            } else {
                this.canStop = false;
            }
        }
        Url url = (Url) getPageCache.poll();
        return url == null ? null : url.url;
    }

    void generateReport() {
        Statement stmt;
        StringBuilder output = new StringBuilder();
        int pages = 0, scanned = 1, notScanned = 0, nette = 0;
        long dbSize = 0;
        try {
            stmt = c.createStatement();
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages")) {
                pages = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE dateComputed IS NOT NULL")) {
                scanned = rs.getInt(1);
            }
            notScanned = pages - scanned;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE useNette = 1")) {
                nette = rs.getInt(1);
            }

            File f = new File(dbFile);
            dbSize = f.length();

        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        output.append("\n\nReport:\n");

        output.append("All addresses with subdomains:\n");
        output.append(String.format(
                "%,d pages, %,d is scanned, %,d waiting to scan\n",
                pages, scanned, notScanned
        ));

        output.append(String.format(
                "%,d pages use Nette, it is %.3f%% of scanned pages\n",
                nette, ((float) nette / scanned) * 100.0
        ));

        try {
            stmt = c.createStatement();
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0")) {
                pages = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND dateComputed IS NOT NULL")) {
                scanned = rs.getInt(1);
            }
            notScanned = pages - scanned;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND useNette = 1")) {
                nette = rs.getInt(1);
            }
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        output.append("\nAll domains without subdomains:\n");
        output.append(String.format(
                "%,d pages, %,d is scanned, %,d waiting to scan\n",
                pages, scanned, notScanned
        ));

        output.append(String.format(
                "%,d pages use Nette, it is %.3f%% of scanned pages\n",
                nette, ((float) nette / scanned) * 100.0
        ));

        try {
            stmt = c.createStatement();
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.sk'")) {
                pages = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.sk' AND dateComputed IS NOT NULL")) {
                scanned = rs.getInt(1);
            }
            notScanned = pages - scanned;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.sk' AND useNette = 1")) {
                nette = rs.getInt(1);
            }
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        output.append("\nOnly .sk domains without subdomains:\n");
        output.append(String.format(
                "%,d pages, %,d is scanned, %,d waiting to scan\n",
                pages, scanned, notScanned
        ));

        output.append(String.format(
                "%,d pages use Nette, it is %.3f%% of scanned pages\n",
                nette, ((float) nette / scanned) * 100.0
        ));

        try {
            stmt = c.createStatement();
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.cz'")) {
                pages = rs.getInt(1);
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.cz' AND dateComputed IS NOT NULL")) {
                scanned = rs.getInt(1);
            }
            notScanned = pages - scanned;
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(id) FROM pages WHERE isSubdomain = 0 AND url LIKE '%.cz' AND useNette = 1")) {
                nette = rs.getInt(1);
            }
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        output.append("\nOnly .cz domains without subdomains:\n");
        output.append(String.format(
                "%,d pages, %,d is scanned, %,d waiting to scan\n",
                pages, scanned, notScanned
        ));

        output.append(String.format(
                "%,d pages use Nette, it is %.3f%% of scanned pages\n",
                nette, ((float) nette / scanned) * 100.0
        ));

        output.append("\ndatabase size is ")
                .append(humanReadableByteCount(dbSize, false));

        output.append("\ncount of process is ").append(java.lang.Thread.activeCount()).append("\n");

        System.out.print(output.toString());
    }

    void export() {
        Statement stmt;
        ResultSet rs;
        boolean actualSecured, previousSecured = false, actualPrinted, previousPrinted = true;
        String actualAddress, previousAddress = "";

        try {
            stmt = c.createStatement();
            rs = stmt.executeQuery("SELECT DISTINCT *, LOWER(REPLACE(url, 'www.', '')) as domain FROM pages " +
                    "WHERE useNette = 1 AND isSubdomain = 0 " +
                    "ORDER BY domain LIMIT 10000000");

            while (rs.next()) {
                actualSecured = rs.getBoolean("secured");
                actualAddress = rs.getString("url");
                actualPrinted = false;

                if (actualAddress.replace("www.", "").equals(previousAddress)) { // actual has www.
                    System.out.println((previousSecured ? "https" : "http") + "://" + previousAddress);
                    actualPrinted = true;
                } else if (previousAddress.replace("www.", "").equals(actualAddress) ||
                        previousAddress.equals(actualAddress)) { // previous has www. || previous is same as actual
                    System.out.println((actualSecured || previousSecured ? "https" : "http") + "://" + actualAddress);
                    actualPrinted = true;
                } else if (!previousPrinted) { // nobody have www, previous is other
                    System.out.println((previousSecured ? "https" : "http") + "://" + previousAddress);
                }

                previousSecured = actualSecured;
                previousAddress = actualAddress;
                previousPrinted = actualPrinted;
            }
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private class Url {

        Integer id;
        String url;

        Url() {

        }

        public Url(Integer id, String url) {
            this.id = id;
            this.url = url;
        }

        Url(String url) {
            this.id = -1;
            this.url = url;
        }
    }

    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public boolean iCanStop() {
        return canStop;
    }

    private int loadPages(int limit, String domain, boolean withoutSubdomain) {
        Statement stmt;
        ResultSet rs;
        Iterator iterator;
        String excludeId;
        Url url;
        StringBuilder sql;
        Integer count = 0;

        if (!getPageCache.isEmpty()) {
            iterator = getPageCache.iterator();
            StringJoiner sj = new StringJoiner(", ");
            while (iterator.hasNext()) {
                url = (Url) iterator.next();
                sj.add(url.id.toString());
            }

            excludeId = sj.toString();
        } else {
            excludeId = "";
        }

        try {
            stmt = c.createStatement();
            sql = new StringBuilder();
            sql.append("SELECT * FROM pages ");
            sql.append("WHERE dateComputed IS NULL ");
            sql.append("AND id NOT IN (").append(excludeId).append(") ");

            if (domain != null) {
                sql.append("AND url LIKE '%.").append(domain).append("' ");
            }
            if (withoutSubdomain) {
                sql.append("AND isSubdomain = 0 ");
            }
            sql.append("ORDER BY dateAdded LIMIT ").append(limit);

            rs = stmt.executeQuery(sql.toString());

            while (rs.next()) {
                url = new Url();
                url.id = rs.getInt("id");
                boolean secured = rs.getBoolean("secured");
                String address = rs.getString("url");

                url.url = (secured ? "https" : "http") + "://" + address;

                getPageCache.add(url);

                count = rs.getRow();
            }
            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Database.class.getName()).log(Level.SEVERE, null, ex);
        }

        return count;
    }
}
