import java.util.*;
import java.util.regex.*;

public class UniversalDriver {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        String metaJson = sc.nextLine().trim();

        String fn       = jsonGetString(metaJson, "fn");
        boolean inPlace = "true".equals(jsonGetBoolean(metaJson, "inPlace"));
        List<String[]> params = jsonGetParams(metaJson); // [name, type]

        Object[]   callArgs   = new Object[params.size()];
        Class<?>[] paramTypes = new Class<?>[params.size()];

        for (int i = 0; i < params.size(); i++) {
            String type = params.get(i)[1];
            String raw  = sc.nextLine().trim();
            callArgs[i]   = parseValue(raw, type);
            paramTypes[i] = getJavaType(type);
        }

        Solution sol = new Solution();
        java.lang.reflect.Method method = sol.getClass().getMethod(fn, paramTypes);
        Object result = method.invoke(sol, callArgs);

        if (inPlace) {
            System.out.println(toJson(callArgs[0]));
        } else {
            System.out.println(toJson(result));
        }
    }

    // ── JSON MINI-PARSER ─────────────────────────────────────────────────

    static String jsonGetString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String jsonGetBoolean(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "false";
    }

    // Returns list of [name, type] pairs from "params":[...]
    static List<String[]> jsonGetParams(String json) {
        List<String[]> result = new ArrayList<>();
        int start = json.indexOf("\"params\"");
        if (start < 0) return result;
        int arrStart = json.indexOf('[', start);
        if (arrStart < 0) return result;

        int depth = 0, arrEnd = arrStart;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) { arrEnd = i; break; } }
        }

        String inner = json.substring(arrStart + 1, arrEnd);
        // split each {...} object
        depth = 0;
        int objStart = -1;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '{') { if (depth == 0) objStart = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = inner.substring(objStart, i + 1);
                    String name = jsonGetString(obj, "name");
                    String type = jsonGetString(obj, "type");
                    if (type != null) result.add(new String[]{name, type});
                    objStart = -1;
                }
            }
        }
        return result;
    }

    // ── PARSE ────────────────────────────────────────────────────────────

    static Object parseValue(String raw, String type) throws Exception {
        if ("int".equals(type))      return Integer.parseInt(raw);
        if ("long".equals(type))     return Long.parseLong(raw);
        if ("double".equals(type))   return Double.parseDouble(raw);
        if ("boolean".equals(type))  return Boolean.parseBoolean(raw);
        if ("string".equals(type))   return raw.replaceAll("^\"|\"$", "");
        if ("int[]".equals(type))    return parseIntArray(raw);
        if ("long[]".equals(type))   return parseLongArray(raw);
        if ("double[]".equals(type)) return parseDoubleArray(raw);
        if ("string[]".equals(type)) return parseStringArray(raw);
        if ("int[][]".equals(type))  return parseIntMatrix(raw);
        if ("char[][]".equals(type)) return parseCharMatrix(raw);
        if ("TreeNode".equals(type)) return buildTree(parseNullableIntArray(raw));
        if ("ListNode".equals(type)) return buildLinkedList(parseIntArray(raw));
        return raw;
    }

    static Class<?> getJavaType(String type) {
        if ("int".equals(type))      return int.class;
        if ("long".equals(type))     return long.class;
        if ("double".equals(type))   return double.class;
        if ("boolean".equals(type))  return boolean.class;
        if ("string".equals(type))   return String.class;
        if ("int[]".equals(type))    return int[].class;
        if ("long[]".equals(type))   return long[].class;
        if ("double[]".equals(type)) return double[].class;
        if ("string[]".equals(type)) return String[].class;
        if ("int[][]".equals(type))  return int[][].class;
        if ("char[][]".equals(type)) return char[][].class;
        if ("TreeNode".equals(type)) return TreeNode.class;
        if ("ListNode".equals(type)) return ListNode.class;
        return Object.class;
    }

    // ── ARRAY PARSERS ────────────────────────────────────────────────────

    static int[] parseIntArray(String s) {
        s = s.trim().replaceAll("^\\[|\\]$", "").trim();
        if (s.isEmpty()) return new int[]{};
        String[] parts = s.split(",");
        int[] res = new int[parts.length];
        for (int i = 0; i < parts.length; i++) res[i] = Integer.parseInt(parts[i].trim());
        return res;
    }

    static long[] parseLongArray(String s) {
        s = s.trim().replaceAll("^\\[|\\]$", "").trim();
        if (s.isEmpty()) return new long[]{};
        String[] parts = s.split(",");
        long[] res = new long[parts.length];
        for (int i = 0; i < parts.length; i++) res[i] = Long.parseLong(parts[i].trim());
        return res;
    }

    static double[] parseDoubleArray(String s) {
        s = s.trim().replaceAll("^\\[|\\]$", "").trim();
        if (s.isEmpty()) return new double[]{};
        String[] parts = s.split(",");
        double[] res = new double[parts.length];
        for (int i = 0; i < parts.length; i++) res[i] = Double.parseDouble(parts[i].trim());
        return res;
    }

    static String[] parseStringArray(String s) {
        s = s.trim().replaceAll("^\\[|\\]$", "").trim();
        if (s.isEmpty()) return new String[]{};
        List<String> result = new ArrayList<>();
        Matcher m = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
        while (m.find()) result.add(m.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        return result.toArray(new String[0]);
    }

    static Integer[] parseNullableIntArray(String s) {
        s = s.trim().replaceAll("^\\[|\\]$", "").trim();
        if (s.isEmpty()) return new Integer[]{};
        String[] parts = s.split(",");
        Integer[] res = new Integer[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            res[i] = "null".equals(p) ? null : Integer.parseInt(p);
        }
        return res;
    }

    static int[][] parseIntMatrix(String s) {
        s = s.trim();
        if ("[]".equals(s)) return new int[][]{};
        s = s.substring(1, s.length() - 1).trim();
        List<int[]> rows = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') { if (depth == 0) start = i; depth++; }
            else if (c == ']') {
                depth--;
                if (depth == 0 && start >= 0) { rows.add(parseIntArray(s.substring(start, i + 1))); start = -1; }
            }
        }
        return rows.toArray(new int[0][]);
    }

    static char[][] parseCharMatrix(String s) {
        s = s.trim();
        if ("[]".equals(s)) return new char[][]{};
        s = s.substring(1, s.length() - 1).trim();
        List<char[]> rows = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') { if (depth == 0) start = i; depth++; }
            else if (c == ']') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String[] strs = parseStringArray(s.substring(start, i + 1));
                    char[] row = new char[strs.length];
                    for (int j = 0; j < strs.length; j++) row[j] = strs[j].charAt(0);
                    rows.add(row);
                    start = -1;
                }
            }
        }
        return rows.toArray(new char[0][]);
    }

    // ── SERIALIZATION ────────────────────────────────────────────────────

    static String toJson(Object val) throws Exception {
        if (val == null)                                          return "null";
        if (val instanceof Integer || val instanceof Long
         || val instanceof Double  || val instanceof Boolean)    return String.valueOf(val);
        if (val instanceof String)                               return (String) val;
        if (val instanceof int[]) {
            int[] a = (int[]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(","); sb.append(a[i]); }
            return sb.append("]").toString();
        }
        if (val instanceof long[]) {
            long[] a = (long[]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(","); sb.append(a[i]); }
            return sb.append("]").toString();
        }
        if (val instanceof double[]) {
            double[] a = (double[]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(","); sb.append(a[i]); }
            return sb.append("]").toString();
        }
        if (val instanceof String[]) {
            String[] a = (String[]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(","); sb.append("\"").append(a[i]).append("\""); }
            return sb.append("]").toString();
        }
        if (val instanceof int[][]) {
            int[][] m = (int[][]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < m.length; i++) { if (i > 0) sb.append(","); sb.append(toJson(m[i])); }
            return sb.append("]").toString();
        }
        if (val instanceof char[][]) {
            char[][] m = (char[][]) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < m.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("[");
                for (int j = 0; j < m[i].length; j++) { if (j > 0) sb.append(","); sb.append("\"").append(m[i][j]).append("\""); }
                sb.append("]");
            }
            return sb.append("]").toString();
        }
        if (val instanceof TreeNode) return serializeTree((TreeNode) val);
        if (val instanceof ListNode) return serializeLinkedList((ListNode) val);
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) { if (i > 0) sb.append(","); sb.append(toJson(list.get(i))); }
            return sb.append("]").toString();
        }
        return String.valueOf(val);
    }

    // ── TREE ─────────────────────────────────────────────────────────────

    static TreeNode buildTree(Integer[] vals) {
        if (vals == null || vals.length == 0 || vals[0] == null) return null;
        TreeNode root = new TreeNode(vals[0]);
        Queue<TreeNode> q = new LinkedList<>();
        q.offer(root);
        int i = 1;
        while (!q.isEmpty() && i < vals.length) {
            TreeNode node = q.poll();
            if (i < vals.length && vals[i] != null) { node.left  = new TreeNode(vals[i]); q.offer(node.left);  } i++;
            if (i < vals.length && vals[i] != null) { node.right = new TreeNode(vals[i]); q.offer(node.right); } i++;
        }
        return root;
    }

    static String serializeTree(TreeNode root) {
        if (root == null) return "[]";
        List<String> res = new ArrayList<>();
        Queue<TreeNode> q = new LinkedList<>();
        q.offer(root);
        while (!q.isEmpty()) {
            TreeNode n = q.poll();
            if (n == null) { res.add("null"); continue; }
            res.add(String.valueOf(n.val));
            q.offer(n.left);
            q.offer(n.right);
        }
        int last = res.size() - 1;
        while (last >= 0 && "null".equals(res.get(last))) last--;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i <= last; i++) { if (i > 0) sb.append(","); sb.append(res.get(i)); }
        return sb.append("]").toString();
    }

    // ── LINKED LIST ──────────────────────────────────────────────────────

    static ListNode buildLinkedList(int[] vals) {
        ListNode dummy = new ListNode(0), cur = dummy;
        for (int v : vals) { cur.next = new ListNode(v); cur = cur.next; }
        return dummy.next;
    }

    static String serializeLinkedList(ListNode head) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        while (head != null) {
            if (!first) sb.append(",");
            sb.append(head.val);
            first = false;
            head = head.next;
        }
        return sb.append("]").toString();
    }
}

// ── Struct definitions ────────────────────────────────────────────────────

class TreeNode {
    int val;
    TreeNode left, right;
    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val; this.left = left; this.right = right;
    }
}

class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
