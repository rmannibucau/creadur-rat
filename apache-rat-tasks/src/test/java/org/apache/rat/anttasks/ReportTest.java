/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rat.anttasks;

import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.BuildException;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.rat.document.impl.guesser.BinaryGuesser;
import org.w3c.dom.Document;

public class ReportTest extends AbstractRatAntTaskTest {
    private static final File antFile = new File("src/test/resources/antunit/report-junit.xml").getAbsoluteFile();

    @Override
    protected File getAntFile() {
        return antFile;
    }

    public void testWithReportSentToAnt() throws Exception {
        executeTarget("testWithReportSentToAnt");
        assertLogMatches("AL +\\Q" + getAntFileName() + "\\E");
    }

    public void testWithReportSentToFile() throws Exception {
        final File reportFile = new File(getTempDir(), "selftest.report");
        if (!getTempDir().mkdirs() && !getTempDir().isDirectory()) {
            throw new IOException("Could not create temporary directory " + getTempDir());
        }
        final String alLine = "AL +\\Q" + getAntFileName() + "\\E";
        if (reportFile.isFile() && !reportFile.delete()) {
            throw new IOException("Unable to remove report file " + reportFile);
        }
        executeTarget("testWithReportSentToFile");
        assertLogDoesNotMatch(alLine);
        Assert.assertTrue("Expected report file " + reportFile, reportFile.isFile());
        assertFileMatches(reportFile, alLine);
    }

    public void testWithALUnknown() throws Exception {
        executeTarget("testWithALUnknown");
        assertLogDoesNotMatch("AL +\\Q" + getAntFileName() + "\\E");
        assertLogMatches("\\!\\?\\?\\?\\?\\? +\\Q" + getAntFileName() + "\\E");
    }

    public void testCustomMatcher() throws Exception {
        executeTarget("testCustomMatcher");
        assertLogDoesNotMatch("AL +\\Q" + getAntFileName() + "\\E");
        assertLogMatches("EXMPL +\\Q" + getAntFileName() + "\\E");
    }

    public void testNoResources() throws Exception {
        try {
            executeTarget("testNoResources");
            fail("Expected Exception");
        } catch (BuildException e) {
            final String expect = "You must specify at least one file";
            assertTrue("Expected " + expect + ", got " + e.getMessage(),
                    e.getMessage().contains(expect));
        }
    }

    public void testNoLicenseMatchers() throws Exception {
        try {
            executeTarget("testNoLicenseMatchers");
            fail("Expected Exception");
        } catch (BuildException e) {
            final String expect = "at least one license";
            assertTrue("Expected " + expect + ", got " + e.getMessage(),
                    e.getMessage().contains(expect));
        }
    }

    private String getAntFileName() {
        return getAntFile().getPath().replace('\\', '/');
    }

    private String getFirstLine(File pFile) throws IOException {
        FileInputStream fis = null;
        InputStreamReader reader = null;
        BufferedReader breader = null;
        try {
            fis = new FileInputStream(pFile);
            reader = new InputStreamReader(fis, "UTF8");
            breader = new BufferedReader(reader);
            final String result = breader.readLine();
            breader.close();
            return result;
        } finally {
            IOUtils.closeQuietly(fis);
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(breader);
        }
    }

    public void testAddLicenseHeaders() throws Exception {
        executeTarget("testAddLicenseHeaders");

        final File origFile = new File("target/anttasks/it-sources/index.apt");
        final String origFirstLine = getFirstLine(origFile);
        assertTrue(origFirstLine.contains("--"));
        assertFalse(origFirstLine.contains("~~"));
        final File modifiedFile = new File("target/anttasks/it-sources/index.apt.new");
        final String modifiedFirstLine = getFirstLine(modifiedFile);
        assertFalse(modifiedFirstLine.contains("--"));
        assertTrue(modifiedFirstLine.contains("~~"));
    }

    /**
     * Test correct generation of string result if non-UTF8 file.encoding is set.
     */
    public void testISO88591() throws Exception {
        String origEncoding = overrideFileEncoding("ISO-8859-1");
        executeTarget("testISO88591");
        overrideFileEncoding(origEncoding);
        assertTrue("Log should contain the test umlauts", getLog().contains("\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF"));
    }

    /**
     * Test correct generation of XML file if non-UTF8 file.encoding is set.
     */
    public void testISO88591WithFile() throws Exception {
        Charset.defaultCharset();
        String outputDir = System.getProperty("output.dir", "target/anttasks");
        String selftestOutput = System.getProperty("report.file", outputDir + "/selftest.report");
        String origEncoding = overrideFileEncoding("ISO-8859-1");
        executeTarget("testISO88591WithReportFile");
        overrideFileEncoding(origEncoding);
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        FileInputStream fis = new FileInputStream(selftestOutput);
        boolean documentParsed = false;
        try {
            Document doc = db.parse(fis);
            boolean byteSequencePresent = doc.getElementsByTagName("header-sample")
                    .item(0)
                    .getTextContent()
                    .contains("\u00E4\u00F6\u00FC\u00C4\u00D6\u00DC\u00DF");
            assertTrue("Report should contain test umlauts", byteSequencePresent);
            documentParsed = true;
        } catch (Exception ex) {
            documentParsed = false;
        } finally {
            fis.close();
        }
        assertTrue("Report file could not be parsed as XML", documentParsed);
    }

    private String overrideFileEncoding(String newEncoding) {
        String current = System.getProperty("file.encoding");
        System.setProperty("file.encoding", newEncoding);
        setBinaryGuesserCharset(newEncoding);
        clearDefaultCharset();
        return current;
    }

    private void clearDefaultCharset() {
        try {
            Field f = Charset.class.getDeclaredField("defaultCharset");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception ex) {
            // This is for unittesting - there is no good reason not to rethrow
            // it. This could be happening in JDK 9, where the unittests need
            // run with the java.base module opened
            throw new RuntimeException(ex);
        }
    }

    private void setBinaryGuesserCharset(String charset) {
        try {
            Field f = BinaryGuesser.class.getDeclaredField("CHARSET_FROM_FILE_ENCODING_OR_UTF8");
            f.setAccessible(true);
            f.set(null, Charset.forName(charset));
        } catch (Exception ex) {
            // This is for unittesting - there is no good reason not to rethrow
            // it. This could be happening in JDK 9, where the unittests need
            // run with the java.base module opened
            throw new RuntimeException(ex);
        }
    }
}
