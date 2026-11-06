package com.first.genProject.service;

import java.util.*;

/** Local extractive summary: source lines only, no invented facts or AI requests. */
public final class InputSummary {
    private InputSummary() { }
    public static String resume(String text) {
        String compact=text.strip().replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n");
        if (compact.length()<=12000) return compact;
        var lines=compact.lines().filter(s -> !s.isBlank()).toList();
        var chosen=new TreeSet<Integer>(); int used=0;
        // Prefer technical sections, preserving their original source order.
        for (int i=0;i<lines.size();i++) if (lines.get(i).toLowerCase(Locale.ROOT).matches(".*(skill|technolog|project|experience|framework|language|database|tool).*")) {
            for (int j=i;j<Math.min(lines.size(),i+4);j++) if (!chosen.contains(j) && used+lines.get(j).length()+1<=12000) {
                chosen.add(j); used+=lines.get(j).length()+1;
            }
        }
        for (int i=0;i<lines.size();i++) if (!chosen.contains(i) && used+lines.get(i).length()+1<=12000) {
            chosen.add(i); used+=lines.get(i).length()+1;
        }
        if (chosen.isEmpty()) return shorten(compact,12000);
        return chosen.stream().map(lines::get).collect(java.util.stream.Collectors.joining("\n"));
    }
    public static String shorten(String value, int limit) {
        if (value.length()<=limit) return value;
        int end=limit; if (Character.isHighSurrogate(value.charAt(end-1))) end--;
        return value.substring(0,end);
    }
}
