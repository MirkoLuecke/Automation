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
    public void parse_buildSuccessAfterNM_returns100() {
        MavenProgressParser p = new MavenProgressParser();
        p.parse("[INFO] Building my-module 1.0.0 [3/9]");
        assertEquals(100, p.parse("[INFO] BUILD SUCCESS").getAsInt());
    }

    @Test
    public void parse_phaseGoalWithNmContext_returnsCombined() {
        MavenProgressParser p = new MavenProgressParser();
        // module 3 of 5: base = (3-1)*100/5 = 40, slot = 100/5 = 20
        // compile phase = 30 -> 40 + 30*20/100 = 46
        p.parse("[INFO] Building my-module 1.0.0 [3/5]");
        assertEquals(46, p.parse(
            "[INFO] --- maven-compiler-plugin:3.8.0:compile (default-compile) @ proj ---").getAsInt());
    }

    @Test
    public void parse_phaseGoalWithNmContext_advancesAcrossModules() {
        MavenProgressParser p = new MavenProgressParser();
        // module 1 install = 0 + 90*20/100 = 18; module 2 base = 20 > 18
        p.parse("[INFO] Building mod 1.0 [1/5]");
        p.parse("[INFO] --- maven-install-plugin:install @ mod ---");
        p.parse("[INFO] Building mod2 1.0 [2/5]");
        // base for module 2 = (2-1)*100/5 = 20, resources = 20 + 15*20/100 = 23
        assertEquals(23, p.parse(
            "[INFO] --- maven-resources-plugin:resources @ mod2 ---").getAsInt());
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
