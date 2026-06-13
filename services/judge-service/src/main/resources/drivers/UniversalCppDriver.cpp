// UniversalCppDriver — compiled with GCC 9.2.0 (C++17) inside the Judge0 sandbox.
//
// Unlike Java/Python/JS, C++ has no runtime reflection — types must be known
// at compile time. The judge service therefore performs *per-submission
// codegen*: CodeBuilder.generateCppDispatch(functionMeta) substitutes the
// dispatch marker (further down) with the exact parsing, dispatch, and
// serialization calls for the function described in functionMeta. The user
// code marker is replaced separately, exactly like the Python / JS drivers.

// The Judge0 sandbox uses GCC 9.2.0 where `<bits/stdc++.h>` is available, but
// the explicit list below keeps the driver portable to any C++17 compiler
// (macOS' Apple clang via libc++ included). The set covers the headers user
// code typically needs for LeetCode-style problems — users normally don't
// need to add their own #includes on top.
#include <algorithm>
#include <array>
#include <bitset>
#include <cctype>
#include <climits>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <functional>
#include <iostream>
#include <list>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <sstream>
#include <stack>
#include <string>
#include <tuple>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>
using namespace std;

// ── Struct definitions (available to user code) ─────────────────────────

struct TreeNode {
    int val;
    TreeNode* left;
    TreeNode* right;
    TreeNode() : val(0), left(nullptr), right(nullptr) {}
    TreeNode(int x) : val(x), left(nullptr), right(nullptr) {}
    TreeNode(int x, TreeNode* l, TreeNode* r) : val(x), left(l), right(r) {}
};

struct ListNode {
    int val;
    ListNode* next;
    ListNode() : val(0), next(nullptr) {}
    ListNode(int x) : val(x), next(nullptr) {}
    ListNode(int x, ListNode* n) : val(x), next(n) {}
};

// ── Driver internals (parsing + serialization) ──────────────────────────

namespace _judge {

inline string trim(const string& s) {
    size_t a = s.find_first_not_of(" \t\r\n");
    if (a == string::npos) return "";
    size_t b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

inline string stripBrackets(const string& raw) {
    string s = trim(raw);
    if (s.size() >= 2 && s.front() == '[' && s.back() == ']') {
        return s.substr(1, s.size() - 2);
    }
    return s;
}

// Split by top-level comma. Tracks `[...]` depth and `"..."` strings so
// nested arrays / quoted commas don't fragment.
inline vector<string> splitTopLevel(const string& s) {
    vector<string> parts;
    string cur;
    int depth = 0;
    bool inStr = false, esc = false;
    for (char c : s) {
        if (inStr) {
            cur += c;
            if (esc) { esc = false; }
            else if (c == '\\') { esc = true; }
            else if (c == '"')  { inStr = false; }
        } else if (c == '"') {
            inStr = true; cur += c;
        } else if (c == '[') {
            depth++; cur += c;
        } else if (c == ']') {
            depth--; cur += c;
        } else if (c == ',' && depth == 0) {
            parts.push_back(trim(cur));
            cur.clear();
        } else {
            cur += c;
        }
    }
    string t = trim(cur);
    if (!t.empty() || !parts.empty()) parts.push_back(t);
    return parts;
}

inline string parseQuotedString(const string& raw) {
    string s = trim(raw);
    if (s.size() >= 2 && s.front() == '"' && s.back() == '"') {
        string r;
        for (size_t i = 1; i + 1 < s.size(); i++) {
            char c = s[i];
            if (c == '\\' && i + 2 < s.size()) {
                char n = s[i + 1];
                switch (n) {
                    case '"':  r += '"';  i++; break;
                    case '\\': r += '\\'; i++; break;
                    case 'n':  r += '\n'; i++; break;
                    case 't':  r += '\t'; i++; break;
                    case 'r':  r += '\r'; i++; break;
                    default:   r += c;        break;
                }
            } else {
                r += c;
            }
        }
        return r;
    }
    return s;
}

inline vector<int> parseIntArray(const string& raw) {
    vector<int> r;
    for (auto& p : splitTopLevel(stripBrackets(raw))) {
        if (!p.empty()) r.push_back(stoi(p));
    }
    return r;
}

inline vector<long long> parseLongArray(const string& raw) {
    vector<long long> r;
    for (auto& p : splitTopLevel(stripBrackets(raw))) {
        if (!p.empty()) r.push_back(stoll(p));
    }
    return r;
}

inline vector<double> parseDoubleArray(const string& raw) {
    vector<double> r;
    for (auto& p : splitTopLevel(stripBrackets(raw))) {
        if (!p.empty()) r.push_back(stod(p));
    }
    return r;
}

inline vector<string> parseStringArray(const string& raw) {
    vector<string> r;
    for (auto& p : splitTopLevel(stripBrackets(raw))) {
        if (!p.empty()) r.push_back(parseQuotedString(p));
    }
    return r;
}

inline vector<vector<int>> parseIntMatrix(const string& raw) {
    vector<vector<int>> r;
    for (auto& row : splitTopLevel(stripBrackets(raw))) {
        if (!row.empty()) r.push_back(parseIntArray(row));
    }
    return r;
}

inline vector<vector<char>> parseCharMatrix(const string& raw) {
    vector<vector<char>> r;
    for (auto& row : splitTopLevel(stripBrackets(raw))) {
        if (row.empty()) continue;
        vector<char> chars;
        for (auto& s : parseStringArray(row)) {
            chars.push_back(s.empty() ? '\0' : s[0]);
        }
        r.push_back(chars);
    }
    return r;
}

inline vector<vector<string>> parseStringMatrix(const string& raw) {
    vector<vector<string>> r;
    for (auto& row : splitTopLevel(stripBrackets(raw))) {
        if (!row.empty()) r.push_back(parseStringArray(row));
    }
    return r;
}

inline TreeNode* buildTree(const string& raw) {
    vector<string> parts = splitTopLevel(stripBrackets(raw));
    if (parts.empty() || parts[0].empty() || parts[0] == "null") return nullptr;
    TreeNode* root = new TreeNode(stoi(parts[0]));
    queue<TreeNode*> q;
    q.push(root);
    size_t i = 1;
    while (!q.empty() && i < parts.size()) {
        TreeNode* n = q.front(); q.pop();
        if (i < parts.size() && !parts[i].empty() && parts[i] != "null") {
            n->left = new TreeNode(stoi(parts[i]));
            q.push(n->left);
        }
        i++;
        if (i < parts.size() && !parts[i].empty() && parts[i] != "null") {
            n->right = new TreeNode(stoi(parts[i]));
            q.push(n->right);
        }
        i++;
    }
    return root;
}

inline ListNode* buildList(const string& raw) {
    vector<int> vals = parseIntArray(raw);
    if (vals.empty()) return nullptr;
    ListNode dummy(0);
    ListNode* cur = &dummy;
    for (int v : vals) {
        cur->next = new ListNode(v);
        cur = cur->next;
    }
    return dummy.next;
}

// ── Serializers ─────────────────────────────────────────────────────────
//
// Two layers, mirroring the Java/JS drivers:
//   - toJsonInner(...): used inside arrays — strings / chars are quoted.
//   - toJson(...):      top-level — strings / chars are unquoted.

inline string toJsonInner(int v)        { return to_string(v); }
inline string toJsonInner(long long v)  { return to_string(v); }
inline string toJsonInner(double v) {
    ostringstream os;
    os.precision(15);
    os << v;
    return os.str();
}
inline string toJsonInner(bool v)         { return v ? "true" : "false"; }
inline string toJsonInner(const string& v) {
    string r = "\"";
    for (char c : v) {
        if (c == '"')       r += "\\\"";
        else if (c == '\\') r += "\\\\";
        else if (c == '\n') r += "\\n";
        else if (c == '\t') r += "\\t";
        else                r += c;
    }
    r += "\"";
    return r;
}
inline string toJsonInner(const char* v) { return toJsonInner(string(v)); }
inline string toJsonInner(char c) {
    string r = "\"";
    r += c;
    r += "\"";
    return r;
}

template <typename T> string toJsonInner(const vector<T>& v);

inline string toJson(int v)         { return to_string(v); }
inline string toJson(long long v)   { return to_string(v); }
inline string toJson(double v) {
    ostringstream os;
    os.precision(15);
    os << v;
    return os.str();
}
inline string toJson(bool v)              { return v ? "true" : "false"; }
inline string toJson(const string& v)     { return v; }              // top-level: unquoted
inline string toJson(const char* v)       { return string(v); }
inline string toJson(char c)              { return string(1, c); }   // top-level: unquoted

template <typename T>
inline string toJson(const vector<T>& v) {
    string r = "[";
    for (size_t i = 0; i < v.size(); i++) {
        if (i > 0) r += ",";
        r += toJsonInner(v[i]);
    }
    r += "]";
    return r;
}

template <typename T>
inline string toJsonInner(const vector<T>& v) {
    return toJson(v);
}

inline string toJson(TreeNode* root) {
    if (!root) return "[]";
    vector<string> res;
    queue<TreeNode*> q;
    q.push(root);
    while (!q.empty()) {
        TreeNode* n = q.front(); q.pop();
        if (!n) { res.push_back("null"); continue; }
        res.push_back(to_string(n->val));
        q.push(n->left);
        q.push(n->right);
    }
    while (!res.empty() && res.back() == "null") res.pop_back();
    string r = "[";
    for (size_t i = 0; i < res.size(); i++) {
        if (i > 0) r += ",";
        r += res[i];
    }
    r += "]";
    return r;
}

inline string toJson(ListNode* head) {
    string r = "[";
    bool first = true;
    while (head) {
        if (!first) r += ",";
        r += to_string(head->val);
        first = false;
        head = head->next;
    }
    r += "]";
    return r;
}

}  // namespace _judge

// === USER_CODE_INJECTED_HERE ===

// Record-separator framing for the batch protocol. One Judge0 submission now
// runs ALL cases of a job in a single process (compile once). The generated
// dispatch prints '\x1e' + "OK\n" + <output> per case; main() frames thrown
// std::exceptions as '\x1e' + "ERR\n" + what(). 0x1E never appears in our answer
// space, so judge-service splits stdout on it. Each case is flushed so finished
// cases survive a later hard crash (segfault) — the remainder is marked RE.
int main() {
    // Line 1 is the functionMeta JSON; types and dispatch are baked into the
    // generated code below, so the meta line is consumed but otherwise ignored.
    std::string _metaLine;
    std::getline(std::cin, _metaLine);

    // Line 2 is the number of cases; the dispatch reads one block of param lines
    // per iteration.
    std::string _tLine;
    std::getline(std::cin, _tLine);
    int _T = 0;
    try { _T = std::stoi(_judge::trim(_tLine)); } catch (...) { _T = 0; }

    for (int _ci = 0; _ci < _T; _ci++) {
        try {
            // === DISPATCH_INJECTED_HERE ===
        } catch (const std::exception& _e) {
            std::cout << '\x1e' << "ERR\n" << _e.what() << "\n";
            std::cout.flush();
        } catch (...) {
            std::cout << '\x1e' << "ERR\nunknown error\n";
            std::cout.flush();
        }
    }

    return 0;
}
