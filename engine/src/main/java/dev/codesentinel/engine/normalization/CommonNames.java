package dev.codesentinel.engine.normalization;

import dev.codesentinel.engine.lexer.Language;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Well-known library names that are NOT renamed to ID_n (only user-defined identifiers are renamed, FR-PLAG-04) and
 * the standard includes that may be suppressed under the common-include policy (FR-PLAG-07/21). The lists are
 * intentionally short and conservative.
 */
final class CommonNames {
    private CommonNames() {
    }

    private static Set<String> of(String s) {
        return new HashSet<>(Arrays.asList(s.trim().split("\\s+")));
    }

    private static final Set<String> C_LIKE_NAMES = of("""
            main printf scanf puts gets getchar putchar fprintf sprintf snprintf sscanf fgets fputs fopen fclose fscanf
            malloc calloc realloc free memset memcpy memmove strlen strcmp strcpy strcat strncpy strncmp abs sqrt pow
            floor ceil fabs rand srand time size_t NULL EOF stdin stdout stderr INT_MAX INT_MIN exit atoi
            std cout cin cerr endl string vector map set unordered_map unordered_set queue stack deque list pair
            make_pair sort swap min max push_back pop_back size begin end first second getline reverse
            """);
    private static final Set<String> JAVA_NAMES = of("""
            main args System out in err println print printf String Integer Long Double Boolean Character Math Scanner
            length size add get put remove contains containsKey List ArrayList LinkedList Map HashMap TreeMap Set HashSet
            Arrays Collections nextInt nextLine next hasNext hasNextInt StringBuilder append toString equals hashCode
            Object Exception RuntimeException Override valueOf parseInt compareTo sort
            """);
    private static final Set<String> PY_NAMES = of("""
            main __name__ __main__ __init__ self print input len range int str float list dict set tuple sum min max abs
            sorted enumerate zip map filter open append pop sort reverse join split strip sys math
            """);
    private static final Set<String> JS_NAMES = of("""
            console log Math Array Object String Number JSON parseInt parseFloat require module exports length push pop
            map filter reduce forEach undefined NaN Infinity process stdin readline on Promise Map Set
            """);

    static boolean isStandardName(Language lang, String name) {
        return switch (lang) {
            case C, CPP -> C_LIKE_NAMES.contains(name);
            case JAVA -> JAVA_NAMES.contains(name);
            case PYTHON -> PY_NAMES.contains(name);
            case JAVASCRIPT -> JS_NAMES.contains(name);
        };
    }

    private static final Set<String> C_HEADERS = of("""
            stdio.h stdlib.h string.h math.h ctype.h limits.h stdbool.h stdint.h time.h assert.h stddef.h float.h
            iostream vector string algorithm map set queue stack deque list cstdio cstdlib cstring cmath ctime climits
            cctype iomanip sstream fstream cassert unordered_map unordered_set numeric utility bits/stdc++.h functional
            """);
    private static final Set<String> PY_MODULES = of("""
            sys math os re collections itertools heapq bisect functools typing random string time datetime
            """);

    static boolean isCommonInclude(Language lang, String text) {
        return switch (lang) {
            case C, CPP -> C_HEADERS.contains(text);
            case JAVA -> text.startsWith("import java.") || text.startsWith("import javax.")
                    || text.startsWith("import static java.");
            case PYTHON -> {
                String[] p = text.split(" ");
                String mod = p.length > 1 ? p[1].replace(",", "") : "";
                int dot = mod.indexOf('.');
                if (dot > 0) mod = mod.substring(0, dot);
                yield PY_MODULES.contains(mod);
            }
            case JAVASCRIPT -> false;
        };
    }
}
