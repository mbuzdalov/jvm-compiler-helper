#!/bin/bash

JAR=jvmch.jar

function pushd_silent {
    pushd "$1" >/dev/null
}

function popd_silent {
    popd >/dev/null
}

function assert_equals {
    if [[ "$1" == "$2" ]]; then
        echo "$3" && return 0
    else
        echo "unexpectedly prints the following" && echo "$2" && return 1
    fi
}

function assert_contains {
    if [ -z "${2##*$1*}" ]; then
        echo "$3" && return 0
    else
        echo "unexpectedly prints the following" && echo "$2" && return 1
    fi
}

function expect_exit_code {
    local EXPECTED_CODE=$1
    shift 1
    "$@" 2>&1
    ACTUAL_CODE="$?"
    if [[ "$EXPECTED_CODE" != "$ACTUAL_CODE" ]]; then
        echo "unexpectedly produced exit code $ACTUAL_CODE" && return 1
    else
        return 0
    fi
}

NO_MAIN_METHOD_MESSAGE=`echo "The JAR file contains no classes with 'public static void main(String[])' or an equivalent construction."`
CLASS_INTERFACE_OR_ENUM_EXPECTED="error: class, interface, or enum"
UNMAPPABLE_CHARACTER="error: unmappable character"

function run_test_01 {
    local JFN=jarfilename.jar
    echo "Running test 01 [public class in root package]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling aplusb.java using JVMCH..." && mkdir temp && \
    java -jar ../$JAR \
        compile-java-files temp $JFN aplusb.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`echo "3 4" | java -jar $JFN` && \
    local EXPECTED=`echo "7"` && assert_equals "$EXPECTED" "$RESULT" "prints 7 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_02 {
    local JFN=jarfilename.jar
    echo "Running test 02 [public class in non-root package]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling print8.java using JVMCH..." && mkdir temp && \
    java -jar ../$JAR \
        compile-java-files temp $JFN print8.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "8"` && assert_equals "$EXPECTED" "$RESULT" "prints 8 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_03 {
    local JFN=jarfilename.jar
    echo "Running test 03 [non-public class in non-root package with mismatching file name]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling print8.java => print9.class using JVMCH..." && mkdir temp && \
    java -jar ../$JAR \
        compile-java-files temp $JFN print8.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "9"` && assert_equals "$EXPECTED" "$RESULT" "prints 9 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_04 {
    local JFN=jarfilename.jar
    echo "Running test 04 [public class in non-root package with mismatching file name]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling print8.java => print9.class using JVMCH..." && mkdir temp && \
    java -jar ../$JAR \
        compile-java-files temp $JFN print8.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "9"` && assert_equals "$EXPECTED" "$RESULT" "prints 9 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_05 {
    local JFN=jarfilename.jar
    echo "Running test 05 [class with public non-static void main]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling print8.java => print9.class using JVMCH..." && mkdir temp && \
    local RESULT=`expect_exit_code 1 java -jar ../$JAR \
        compile-java-files temp $JFN print8.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN` && \
    assert_equals "$NO_MAIN_METHOD_MESSAGE" "$RESULT" " found no PSVM as expected." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_06 {
    local JFN=jarfilename.jar
    echo "Running test 06 [nonsense instead of sources]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling source.java using JVMCH..." && mkdir temp && \
    local RESULT=`expect_exit_code 1 java -jar ../$JAR \
        compile-java-files temp $JFN source.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN` && \
    assert_contains "$CLASS_INTERFACE_OR_ENUM_EXPECTED" "$RESULT" " found no sources as expected." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_07 {
    local JFN=jarfilename.jar
    echo "Running test 07 [binary nonsense instead of sources]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling source.java using JVMCH..." && \
    local RESULT=`expect_exit_code 1 java -jar ../$JAR \
        compile-java-files temp $JFN source.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN` && \
    assert_contains "$UNMAPPABLE_CHARACTER" "$RESULT" " found no sources as expected." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_08 {
    local JFN=jarfilename.jar
    echo "Running test 08 [two public classes in root package]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling [data.java main.java] using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN data.java main.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "10"` && assert_equals "$EXPECTED" "$RESULT" "prints 10 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_09 {
    local JFN=jarfilename.jar
    echo "Running test 09 [two non-public classes in different non-trivial packages]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling [data.java main.java] using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN data.java main.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "10"` && assert_equals "$EXPECTED" "$RESULT" "prints 10 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_10 {
    local JFN=jarfilename.jar
    echo "Running test 10 [as above + one of the files somewhere else]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done." && \
    echo -n "  Compiling [nontrivial/location/data.java main.java] using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN nontrivial/location/data.java main.java \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "10"` && assert_equals "$EXPECTED" "$RESULT" "prints 10 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN && echo " done."
}

function run_test_11 {
    local JFN1=jf1.jar
    local JFN2=jf2.jar
    local JFN=jf.jar
    echo "Running test 11 [separate compilation, merging, main detection]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN $JFN1 $JFN2 && echo " done." && \
    echo -n "  Compiling data.java using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN1 data.java \
        && echo " done." && \
    echo -n "  Compiling main.java using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN2 main.java \
        && echo " done." && \
    echo -n "  Merging the jars..." && \
    java -jar ../$JAR \
        merge-jar-files $JFN $JFN1 $JFN2 \
        && echo " done." && \
    echo -n "  Main-annotating..." && \
    java -jar ../$JAR \
        annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "10"` && assert_equals "$EXPECTED" "$RESULT" "prints 10 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN $JFN1 $JFN2 && echo " done."
}

function run_test_12 {
    local JFN1=jf1.jar
    local JFN2=jf2.jar
    local JFN=jf.jar
    echo "Running test 12 [same as above as a single command]..." && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN $JFN1 $JFN2 && echo " done." && \
    echo -n "  Compiling everything using JVMCH..." && \
    java -jar ../$JAR \
        compile-java-files temp $JFN1 data.java \
        \# compile-java-files temp $JFN2 main.java \
        \# merge-jar-files $JFN $JFN1 $JFN2 \
        \# annotate-jar-with-main-class-attribute $JFN $JFN \
        && echo " done." && \
    echo -n "  Running the result... " && local RESULT=`java -jar $JFN` && \
    local EXPECTED=`echo "10"` && assert_equals "$EXPECTED" "$RESULT" "prints 10 as expected" && \
    echo -n "  Cleaning up..." && rm -rf temp $JFN $JFN1 $JFN2 && echo " done."
}

function run_tests {
    pushd_silent 01 && run_test_01 && popd_silent && \
    pushd_silent 02 && run_test_02 && popd_silent && \
    pushd_silent 03 && run_test_03 && popd_silent && \
    pushd_silent 04 && run_test_04 && popd_silent && \
    pushd_silent 05 && run_test_05 && popd_silent && \
    pushd_silent 06 && run_test_06 && popd_silent && \
    pushd_silent 07 && run_test_07 && popd_silent && \
    pushd_silent 08 && run_test_08 && popd_silent && \
    pushd_silent 09 && run_test_09 && popd_silent && \
    pushd_silent 10 && run_test_10 && popd_silent && \
    pushd_silent 11 && run_test_11 && popd_silent && \
    pushd_silent 12 && run_test_12 && popd_silent
}

pushd_silent .. && \
ant clean jar && \
mv $JAR tests && \
ant clean && \
popd_silent && \
run_tests && \
rm $JAR

