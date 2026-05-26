package com.example.automation.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.example.automation.actions.MavenProgressParser;

public class MavenProgressParserTest {

    @Test
    public void parse_nMBuildingLine_returnsN1Fraction() {
        MavenProgressParser p = new MavenProgressParser();
        // (3-1)*100/9 = 22
        assertEquals(22, p.parse("[INFO] Building my-module 1.0.0 [3/9]").getAsInt());
    }

    @Test
    public void parse_buildSuccessAfterNM_returnsNFraction() {
        MavenProgressParser p = new MavenProgressParser();
        p.parse("[INFO] Building my-module 1.0.0 [3/9]");
        // 3*100/9 = 33
        assertEquals(33, p.parse("[INFO] BUILD SUCCESS").getAsInt());
    }

    @Test
    public void parse_buildSuccessNoNM_returns100() {
        assertEquals(100, new MavenProgressParser().parse("[INFO] BUILD SUCCESS").getAsInt());
    }

    @Test
    public void parse_compileGoal_returns30() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(30, p.parse(
            "[INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ proj ---").getAsInt());
    }

    @Test
    public void parse_testCompileGoal_returns45() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(45, p.parse(
            "[INFO] --- maven-compiler-plugin:3.8.0:testCompile (default-testCompile) @ proj ---").getAsInt());
    }

    @Test
    public void parse_testGoal_returns60() {
        MavenProgressParser p = new MavenProgressParser();
        assertEquals(60, p.parse(
            "[INFO] --- maven-surefire-plugin:2.22.2:test (default-test) @ proj ---").getAsInt());
    }

    @Test
    public void parse_buildFailure_returnsEmpty() {
        assertFalse(new MavenProgressParser().parse("[INFO] BUILD FAILURE").isPresent());
    }

    @Test
    public void parse_unrecognisedLine_returnsEmpty() {
        assertFalse(new MavenProgressParser().parse("[INFO] Scanning for projects...").isPresent());
    }
}
