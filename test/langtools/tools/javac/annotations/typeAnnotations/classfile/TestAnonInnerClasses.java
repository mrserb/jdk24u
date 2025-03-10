/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8005085 8008762 8008751 8013065 8015323 8015257
 * @summary Type annotations on anonymous and inner class.
 *  Six TYPE_USE annotations are repeated(or not); Four combinations create
 *  four test files, and each results in the test class and 2 anonymous classes.
 *  Each element of these three classes is checked for expected number of the
 *  four annotation Attributes. Expected annotation counts depend on type of
 *  annotation place on type of element (a FIELD&TYPE_USE element on a field
 *  results in 2). Elements with no annotations expect 0.
 *  Source template is read in from testanoninner.template
 */
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.charset.*;
import java.io.File;
import java.io.IOException;


import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import static java.lang.annotation.ElementType.*;

/*
 * A source template is read in and testname and annotations are inserted
 * via replace().
 */
public class TestAnonInnerClasses extends ClassfileTestHelper {
    // tally errors and test cases
    int errors = 0;
    int checks = 0;
    //Note expected test count in case of skips due to bugs.
    int tc = 0, xtc = 180; // 45 x 4 variations of repeated annotations.
    File testSrc = new File(System.getProperty("test.src"));

    AttributeMapper<?> [] AnnoAttributes = new AttributeMapper<?>[]{
            Attributes.runtimeVisibleTypeAnnotations(),
            Attributes.runtimeInvisibleTypeAnnotations(),
            Attributes.runtimeVisibleAnnotations(),
            Attributes.runtimeInvisibleAnnotations()
    };

    // template for source files
    String srcTemplate = "testanoninner.template";

    // Four test files generated based on combinations of repeating annotations.
    Boolean As= false, Bs=true, Cs=false, Ds=false, TAs=false,TBs=false;
    Boolean[][] bRepeat = new Boolean[][]{
                 /* no repeats    */ {false, false, false, false, false, false},
                 /* repeat A,C,TA */ {true,  false, true,  false, true,  false},
                 /* repeat B,D,TB */ {false, true,  false, true,  false, true},
                 /* repeat all    */ {true,  true,  true,  true,  true,  true}
    };
    // Save descriptions of failed test case; does not terminate upon a failure.
    List<String> failed = new LinkedList<>();

    public static void main(String[] args) throws Exception {
        new TestAnonInnerClasses().run();
    }

    // Check annotation counts and make reports sufficiently descriptive to
    // easily diagnose.
    void check(String testcase, int vtaX, int itaX, int vaX, int iaX,
                                int vtaA, int itaA, int vaA, int iaA) {

        String descr = " checking " + testcase+" _TYPE_, expected: " +
            vtaX + ", " + itaX + ", " + vaX + ", " + iaX + "; actual: " +
            vtaA + ", " + itaA + ", " + vaA + ", " + iaA;
        String description;
        description=descr.replace("_TYPE_","RuntimeVisibleTypeAnnotations");
        if (vtaX != vtaA) {
            errors++;
            failed.add(++checks + " " + testcase + ": (vtaX) " + vtaX +
                       " != " + vtaA + " (vtaA)");
            println(checks + " FAIL: " + description);
        } else {
            println(++checks + " PASS: " + description);
        }
        description=descr.replace("_TYPE_","RuntimeInvisibleTypeAnnotations");
        if (itaX != itaA) {
            errors++;
            failed.add(++checks + " " + testcase + ": (itaX) " + itaX + " != " +
                       itaA + " (itaA)");
            println(checks + " FAIL: " + description);
        } else {
            println(++checks + " PASS: " + description);
        }
        description=descr.replace("_TYPE_","RuntimeVisibleAnnotations");
        if (vaX != vaA) {
            errors++;
            failed.add(++checks + " " + testcase + ": (vaX) " + vaX + " != " +
                       vaA + " (vaA)");
            println(checks + " FAIL: " + description);
        } else {
            println(++checks + " PASS: " + description);
        }
        description=descr.replace("_TYPE_","RuntimeInvisibleAnnotations");
        if (iaX != iaA) {
            errors++;
            failed.add(++checks + " " + testcase + ": (iaX) " + iaX + " != " +
                       iaA + " (iaA)");
            println(checks + " FAIL: " + description);
        } else {
            println(++checks + " PASS: " + description);
        }
        println("");
    }

    // Print failed cases (if any) and throw exception for fail.
    void report() {
        if (errors!=0) {
            System.err.println("Failed tests: " + errors +
                               "\nfailed test cases:\n");
            for (String t: failed) System.err.println("  " + t);
            throw new RuntimeException("FAIL: There were test failures.");
        } else
            System.out.println("PASSED all tests.");
    }

    <T extends Attribute<T>> void test(AttributedElement m) {
        int vtaActual = 0,
            itaActual = 0,
            vaActual = 0,
            iaActual = 0,
            vtaExp = 0,
            itaExp = 0,
            vaExp = 0,
            iaExp = 0,
            index = 0,
            index2 = 0;
        String memberName = null,
            testcase = "undefined",
            testClassName = null;
        Attribute<T> attr = null,
            cattr = null;
        CodeAttribute CAttr = null;
        // Get counts of 4 annotation Attributes on element being checked.
        for (AttributeMapper<?> Anno : AnnoAttributes) {
            AttributeMapper<T> AnnoType = (AttributeMapper<T>) Anno;
            if (Objects.requireNonNull(m) instanceof ClassModel) {
                ClassModel cm = (ClassModel) m;
                memberName = cm.thisClass().name().stringValue();
                attr = m.findAttribute(AnnoType).orElse(null);
            } else {
                memberName = m instanceof MethodModel ?
                        ((MethodModel) m).methodName().stringValue() : ((FieldModel) m).fieldName().stringValue();
                attr = m.findAttribute(AnnoType).orElse(null);
                //fetch index annotations from code attribute.
                CAttr = m.findAttribute(Attributes.code()).orElse(null);
                if (CAttr != null) {
                    cattr = CAttr.findAttribute(AnnoType).orElse(null);
                }
            }
            ;
//        testClassName=cm.getName();
//        testcase = ttype + ": " + testClassName + ": " +
//                memberName + ", ";
            if (attr != null) {
                switch (attr) {
                    case RuntimeVisibleTypeAnnotationsAttribute RVTAa -> //count RuntimeVisibleTypeAnnotations
                            vtaActual += RVTAa.annotations().size();
                    case RuntimeVisibleAnnotationsAttribute RVAa -> //count RuntimeVisibleAnnotations
                            vaActual += RVAa.annotations().size();
                    case RuntimeInvisibleTypeAnnotationsAttribute RITAa -> //count RuntimeInvisibleTypeAnnotations
                            itaActual += RITAa.annotations().size();
                    case RuntimeInvisibleAnnotationsAttribute RIAa -> //count RuntimeInvisibleAnnotations
                            iaActual += RIAa.annotations().size();
                    default -> throw new AssertionError();
                }
            }
            // annotations from code attribute.
            if (cattr != null) {
                switch (cattr) {
                    case RuntimeVisibleTypeAnnotationsAttribute RVTAa -> //count RuntimeVisibleTypeAnnotations
                            vtaActual += RVTAa.annotations().size();
                    case RuntimeVisibleAnnotationsAttribute RVAa -> //count RuntimeVisibleAnnotations
                            vaActual += RVAa.annotations().size();
                    case RuntimeInvisibleTypeAnnotationsAttribute RITAa -> //count RuntimeInvisibleTypeAnnotations
                            itaActual += RITAa.annotations().size();
                    case RuntimeInvisibleAnnotationsAttribute RIAa -> //count RuntimeInvisibleAnnotations
                            iaActual += RIAa.annotations().size();
                    default -> throw new AssertionError();
                }
            }
        }

        switch (memberName) {
            //METHODs
            case "test" : vtaExp=4;  itaExp=4;  vaExp=0; iaExp=0; tc++; break;
            case "mtest": vtaExp=4;  itaExp=4;  vaExp=1; iaExp=1; tc++; break;
            case "m1":    vtaExp=2;  itaExp=2;  vaExp=1; iaExp=1; tc++; break;
            case "m2":    vtaExp=4;  itaExp=4;  vaExp=1; iaExp=1; tc++; break;
            case "m3":    vtaExp=10; itaExp=10; vaExp=1; iaExp=1; tc++; break;
            case "tm":    vtaExp=6;  itaExp=6;  vaExp=1; iaExp=1; tc++; break;
            //inner class
            case "i_m1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "i_m2":  vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            case "i_um":  vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            //local class
            case "l_m1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "l_m2":  vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            case "l_um":  vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            //anon class
            case "mm_m1": vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "mm_m2": vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            case "mm_m3": vtaExp=10; itaExp=10;vaExp=1; iaExp=1; tc++; break;
            case "mm_tm": vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            //InnerAnon class
            case "ia_m1": vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "ia_m2": vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            case "ia_um": vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            //FIELDs
            case "data":   vtaExp = 2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "odata1": vtaExp = 2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "pdata1": vtaExp = 2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "tdata":  vtaExp = 2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "sa1":    vtaExp = 6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            //inner class
            case "i_odata1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "i_pdata1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "i_udata":   vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "i_sa1":     vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            case "i_tdata":   vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            //local class
            case "l_odata1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "l_pdata1":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "l_udata":   vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "l_sa1":     vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            case "l_tdata":   vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            //anon class
            case "mm_odata1": vtaExp = 2; itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "mm_pdata1": vtaExp = 2; itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "mm_sa1":    vtaExp = 6; itaExp=6; vaExp=1; iaExp=1; tc++; break;
            case "mm_tdata":  vtaExp = 2; itaExp=2; vaExp=1; iaExp=1; tc++; break;
            // InnerAnon class
            case "ia_odata1": vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "ia_pdata1": vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "ia_udata":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "ia_sa1":    vtaExp=6;  itaExp=6; vaExp=1; iaExp=1; tc++; break;
            case "ia_tdata":  vtaExp=2;  itaExp=2; vaExp=1; iaExp=1; tc++; break;
            case "IA":        vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            case "IN":        vtaExp=4;  itaExp=4; vaExp=1; iaExp=1; tc++; break;
            // default cases are <init>, this$0, this$1, mmtest, atest
            default:          vtaExp = 0;  itaExp=0; vaExp=0; iaExp=0;    break;
        }
        check(testcase,vtaExp,   itaExp,   vaExp,   iaExp,
                       vtaActual,itaActual,vaActual,iaActual);
    }

    public <T extends Attribute<T>>void run() {
        ClassModel cm  = null;
        InputStream in = null;
        int testcount  = 1;
        File testFile  = null;
        // Generate source, check methods and fields for each combination.
        for (Boolean[] bCombo : bRepeat) {
            As=bCombo[0]; Bs=bCombo[1]; Cs=bCombo[2];
            Ds=bCombo[3]; TAs=bCombo[4]; TBs=bCombo[5];
            String testname = "Test" + testcount++;
            println("Combinations: " + As + ", " + Bs + ", " + Cs + ", " + Ds +
                    ", " + TAs + ", " + TBs +
                    "; see " + testname + ".java");
            String[] classes = {testname + ".class",
                                testname + "$Inner.class",
                                testname + "$1Local1.class",
                                testname + "$1.class",
                                testname + "$1$1.class",
                                testname + "$1$InnerAnon.class"
            };
            // Create test source, create and compile File.
            String sourceString = getSource(srcTemplate, testname,
                                            As, Bs, Cs, Ds, TAs, TBs);
            System.out.println(sourceString);
            try {
                testFile = writeTestFile(testname+".java", sourceString);
            }
            catch (IOException ioe) { ioe.printStackTrace(); }
            // Compile test source and read classfile.
            File classFile = null;
            try {
                classFile = compile(testFile);
            }
            catch (Error err) {
                System.err.println("FAILED compile. Source:\n" + sourceString);
                throw err;
            }
            String testloc = classFile.getAbsolutePath().substring(
                   0,classFile.getAbsolutePath().indexOf(classFile.getPath()));
            for (String clazz : classes) {
                try {
                    cm = ClassFile.of().parse(new File(testloc+clazz).toPath());
                }
                catch (Exception e) { e.printStackTrace();  }
                // Test for all methods and fields
                assert cm != null;
                for (MethodModel m: cm.methods()) {
                    test(m);
                }
                for (FieldModel f: cm.fields()) {
                    test(f);
                }
            }
        }
        report();
        if (tc!=xtc) System.out.println("Test Count: " + tc + " != " +
                                       "expected: " + xtc);
    }


    String getSrcTemplate(String sTemplate) {
        List<String> tmpl = null;
        String sTmpl = "";
        try {
            tmpl = Files.readAllLines(new File(testSrc,sTemplate).toPath(),
                                      Charset.defaultCharset());
        }
        catch (IOException ioe) {
            String error = "FAILED: Test failed to read template" + sTemplate;
            ioe.printStackTrace();
            throw new RuntimeException(error);
        }
        for (String l : tmpl)
            sTmpl=sTmpl.concat(l).concat("\n");
        return sTmpl;
    }

    // test class template
    String getSource(String templateName, String testname,
                     Boolean Arepeats,  Boolean Brepeats,
                     Boolean Crepeats,  Boolean Drepeats,
                     Boolean TArepeats, Boolean TBrepeats) {
        String As  = Arepeats  ? "@A @A":"@A",
               Bs  = Brepeats  ? "@B @B":"@B",
               Cs  = Crepeats  ? "@C @C":"@C",
               Ds  = Drepeats  ? "@D @D":"@D",
               TAs = TArepeats ? "@TA @TA":"@TA",
               TBs = TBrepeats ? "@TB @TB":"@TB";

        // split up replace() lines for readability
        String testsource = getSrcTemplate(templateName).replace("testname",testname);
        testsource = testsource.replace("_As",As).replace("_Bs",Bs).replace("_Cs",Cs);
        testsource = testsource.replace("_Ds",Ds).replace("_TAs",TAs).replace("_TBs",TBs);
        return testsource;
    }
}
