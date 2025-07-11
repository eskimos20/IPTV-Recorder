package se.eskimos.recorder;

import java.util.Arrays;
import java.util.List;

public class SportsEventsHelper {
    /**
     * SPORTS_KEYWORDS contains sport names and relevant phrases (like 'etapp') in the following languages:
     * Swedish, English, Finnish, Norwegian, Danish, Italian, Spanish, and French.
     *
     * Only these languages are included. Add new sports or relevant phrases in these languages only.
     */
    public static final List<String> SPORTS_KEYWORDS = Arrays.asList(
        // Football
        "fotboll", "football", "soccer", "fútbol", "futbol", "calcio", "jalkapallo", "fotball", "fodbold",
        // Ice hockey
        "ishockey", "ice hockey", "hockey sur glace", "hockey sobre hielo", "hockey su ghiaccio", "jääkiekkö", "hockey", "hockey su ghiaccio", "hockey sobre hielo",
        // Handball
        "handboll", "handball", "balonmano", "pallamano", "käsipallo", "håndball", "håndbold",
        // Basketball
        "basket", "basketball", "baloncesto", "pallacanestro", "koripallo", "basketbol", "basket",
        // Tennis
        "tennis", "tenis", "tennis de table", "tennis tavolo", "tennis mesa", "tennis de mesa",
        // Table tennis
        "bordtennis", "table tennis", "pingis", "tennis de table", "tenis de mesa", "pöytätennis",
        // Badminton
        "badminton", "bádminton",
        // Baseball
        "baseboll", "baseball", "béisbol", "beisebol",
        // Rugby
        "rugby", "rugby league", "rugby union",
        // American football
        "amerikansk fotboll", "american football", "futbol americano",
        // Floorball
        "innebandy", "floorball", "salibandy",
        // Volleyball
        "volleyboll", "volleyball", "voleibol", "pallavolo", "lentopallo",
        // Athletics
        "friidrott", "athletics", "track and field", "atletismo", "atletica", "yleisurheilu",
        // Swimming
        "simning", "swimming", "natation", "natación", "nuoto", "uinti",
        // Cycling and cycling events
        "cykel", "cycling", "ciclismo", "pyöräily", "sykling", "sykkel", "ciclisme", "bicicletta",
        "Tour de France", "Giro d'Italia", "Vuelta a España", "Paris-Roubaix", "Milano-Sanremo", "Il Lombardia", "Tirreno-Adriatico", "Critérium du Dauphiné", "La Flèche Wallonne", "Liège-Bastogne-Liège", "Amstel Gold Race", "Strade Bianche", "Gent-Wevelgem", "Dwars door Vlaanderen", "E3 Saxo Bank Classic", "Omloop Het Nieuwsblad",
        // Stage
        "etapp", "stage", "etape", "tappa", "etapa",
        // Skiing
        "skidåkning", "skiing", "sci", "esquí", "hiihto", "langrenn", "alpint", "sci di fondo",
        // Snowboard
        "snowboard", "snowboarding", "snowboard", "snowboard",
        // Gymnastics
        "gymnastik", "gymnastics", "gimnasia", "ginnastica", "voimistelu",
        // Martial arts
        "kampsport", "martial arts", "arti marziali", "artes marciales", "kamppailulajit",
        // Boxing
        "boxning", "boxing", "boxe", "boxeo", "nyrkkeily",
        // Wrestling
        "brottning", "wrestling", "lucha libre", "lotta", "paini",
        // Fencing
        "fäktning", "fencing", "escrime", "esgrima", "miekkailu",
        // Equestrian
        "ridsport", "equestrian", "équitation", "equitación", "ratsastus",
        // Running
        "löpning", "running", "corsa", "carrera", "juoksu",
        // Diving
        "simhopp", "diving", "plongeon", "clavados", "tuffi",
        // Canoeing
        "kanot", "canoeing", "canoë", "piragüismo", "melonta",
        // Sailing
        "segling", "sailing", "voile", "vela", "purjehdus",
        // Triathlon
        "triathlon", "triatlón", "triatlo", "triatlon",
        // Golf
        "golf", "golf", "golf", "golf", "golf", "golf", "golf", "golf"
    );

    /**
     * Extracts all matching sports/events and stage numbers from a channel name.
     * Returns an array of all unique matches (capitalized, sanitized for filenames).
     * If a stage/etapp number is found (e.g. 'Etapp 5', 'Stage 3'), it is included as 'Etapp_5'.
     */
    public static String[] extractAllEventsAndStages(String channelName) {
        if (channelName == null) return new String[0];
        String name = channelName;
        // Remove time prefix if present (e.g., '13:15 ')
        name = name.replaceFirst("^\\d{1,2}:\\d{2} ", "").trim();
        // Split on '|' or '[' or other separators
        String[] parts = name.split("\\||\\[");
        java.util.LinkedHashSet<String> matches = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> stageWordsWithNumber = new java.util.LinkedHashSet<>();
        String[] stageWords = {"Etapp", "Stage", "Etape", "Tappa", "Etapa"};
        for (String part : parts) {
            for (String keyword : SPORTS_KEYWORDS) {
                if (part.toLowerCase().contains(keyword.toLowerCase())) {
                    // Capitalize and sanitize
                    String cap = keyword.substring(0, 1).toUpperCase() + keyword.substring(1);
                    String sanitized = cap.replaceAll("[^A-Za-z0-9]", "_");
                    matches.add(sanitized);
                }
            }
            // Special: extract stage/etapp number (e.g. 'Etapp 5', 'Stage 3')
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(etapp|stage|etape|tappa|etapa)[ ]?(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(part);
            while (m.find()) {
                String stageWord = m.group(1);
                String stageNum = m.group(2);
                String stageSanitized = stageWord.substring(0, 1).toUpperCase() + stageWord.substring(1).toLowerCase() + "_" + stageNum;
                stageWordsWithNumber.add(stageSanitized.replaceAll("[^A-Za-z0-9_]", "_"));
            }
        }
        // Remove plain stage words if a numbered version exists
        for (String stageWord : stageWords) {
            String sanitized = stageWord.replaceAll("[^A-Za-z0-9]", "_");
            boolean hasNumbered = false;
            for (String s : stageWordsWithNumber) {
                if (s.startsWith(sanitized + "_")) {
                    hasNumbered = true;
                    break;
                }
            }
            if (hasNumbered) {
                matches.remove(sanitized);
            }
        }
        matches.addAll(stageWordsWithNumber);
        return matches.toArray(new String[0]);
    }
} 