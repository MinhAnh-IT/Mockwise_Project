"""Correct Java / JavaScript / C++ reference solutions for ALL 111 problems.

verify_drivers.py runs each of these through the real Universal{Java,Js,Cpp}
driver on every generated testcase and checks the output against the
Python-derived expected value. Passing across all four languages proves the
testcases are language-agnostic and the whole bank is runnable as the system
advertises (and catches per-problem divergences like Java/C++ int overflow).
"""

SOLUTIONS = {}

# ════════════════════════ EASY ════════════════════════
SOLUTIONS.update({

    "Number of 1 Bits": {
        "java": "class Solution { public int hammingWeight(int n){return Integer.bitCount(n);} }",
        "js": "var hammingWeight=function(n){let c=0;while(n!==0){n&=(n-1);c++;}return c;};",
        "cpp": "class Solution { public: int hammingWeight(int n){int c=0;unsigned u=n;while(u){u&=(u-1);c++;}return c;} };",
    },
    "Counting Bits": {
        "java": "class Solution { public int[] countBits(int n){int[] dp=new int[n+1];for(int i=1;i<=n;i++)dp[i]=dp[i>>1]+(i&1);return dp;} }",
        "js": "var countBits=function(n){const dp=new Array(n+1).fill(0);for(let i=1;i<=n;i++)dp[i]=dp[i>>1]+(i&1);return dp;};",
        "cpp": "class Solution { public: vector<int> countBits(int n){vector<int> dp(n+1,0);for(int i=1;i<=n;i++)dp[i]=dp[i>>1]+(i&1);return dp;} };",
    },
    "Missing Number": {
        "java": "class Solution { public int missingNumber(int[] nums){int n=nums.length;int s=n*(n+1)/2;for(int v:nums)s-=v;return s;} }",
        "js": "var missingNumber=function(nums){let n=nums.length,s=n*(n+1)/2;for(const v of nums)s-=v;return s;};",
        "cpp": "class Solution { public: int missingNumber(vector<int>& nums){int n=nums.size();int s=n*(n+1)/2;for(int v:nums)s-=v;return s;} };",
    },
    "Single Number": {
        "java": "class Solution { public int singleNumber(int[] nums){int x=0;for(int v:nums)x^=v;return x;} }",
        "js": "var singleNumber=function(nums){let x=0;for(const v of nums)x^=v;return x;};",
        "cpp": "class Solution { public: int singleNumber(vector<int>& nums){int x=0;for(int v:nums)x^=v;return x;} };",
    },
    "Majority Element": {
        "java": "class Solution { public int majorityElement(int[] nums){int c=0,cand=0;for(int v:nums){if(c==0)cand=v;c+=(v==cand)?1:-1;}return cand;} }",
        "js": "var majorityElement=function(nums){let c=0,cand=0;for(const v of nums){if(c===0)cand=v;c+=(v===cand)?1:-1;}return cand;};",
        "cpp": "class Solution { public: int majorityElement(vector<int>& nums){int c=0,cand=0;for(int v:nums){if(c==0)cand=v;c+=(v==cand)?1:-1;}return cand;} };",
    },
    "Move Zeroes": {
        "java": "class Solution { public void moveZeroes(int[] nums){int last=0;for(int i=0;i<nums.length;i++){if(nums[i]!=0){int t=nums[last];nums[last]=nums[i];nums[i]=t;last++;}}} }",
        "js": "var moveZeroes=function(nums){let last=0;for(let i=0;i<nums.length;i++){if(nums[i]!==0){const t=nums[last];nums[last]=nums[i];nums[i]=t;last++;}}};",
        "cpp": "class Solution { public: void moveZeroes(vector<int>& nums){int last=0;for(size_t i=0;i<nums.size();i++){if(nums[i]!=0){swap(nums[last],nums[i]);last++;}}} };",
    },
    "Squares of a Sorted Array": {
        "java": "class Solution { public int[] sortedSquares(int[] nums){int n=nums.length;int[] r=new int[n];int l=0,h=n-1;for(int i=n-1;i>=0;i--){if(Math.abs(nums[l])>Math.abs(nums[h])){r[i]=nums[l]*nums[l];l++;}else{r[i]=nums[h]*nums[h];h--;}}return r;} }",
        "js": "var sortedSquares=function(nums){let n=nums.length,r=new Array(n),l=0,h=n-1;for(let i=n-1;i>=0;i--){if(Math.abs(nums[l])>Math.abs(nums[h])){r[i]=nums[l]*nums[l];l++;}else{r[i]=nums[h]*nums[h];h--;}}return r;};",
        "cpp": "class Solution { public: vector<int> sortedSquares(vector<int>& nums){int n=nums.size();vector<int> r(n);int l=0,h=n-1;for(int i=n-1;i>=0;i--){if(abs(nums[l])>abs(nums[h])){r[i]=nums[l]*nums[l];l++;}else{r[i]=nums[h]*nums[h];h--;}}return r;} };",
    },
    "Plus One": {
        "java": "class Solution { public int[] plusOne(int[] d){int[] a=d.clone();for(int i=a.length-1;i>=0;i--){if(a[i]<9){a[i]++;return a;}a[i]=0;}int[] r=new int[a.length+1];r[0]=1;return r;} }",
        "js": "var plusOne=function(d){d=d.slice();for(let i=d.length-1;i>=0;i--){if(d[i]<9){d[i]++;return d;}d[i]=0;}return [1].concat(d);};",
        "cpp": "class Solution { public: vector<int> plusOne(vector<int>& d){vector<int> a=d;for(int i=a.size()-1;i>=0;i--){if(a[i]<9){a[i]++;return a;}a[i]=0;}a.insert(a.begin(),1);return a;} };",
    },
    "Climbing Stairs": {
        "java": "class Solution { public int climbStairs(int n){int a=1,b=1;for(int i=0;i<n;i++){int t=a+b;a=b;b=t;}return a;} }",
        "js": "var climbStairs=function(n){let a=1,b=1;for(let i=0;i<n;i++){const t=a+b;a=b;b=t;}return a;};",
        "cpp": "class Solution { public: int climbStairs(int n){int a=1,b=1;for(int i=0;i<n;i++){int t=a+b;a=b;b=t;}return a;} };",
    },
    "Fibonacci Number": {
        "java": "class Solution { public int fib(int n){int a=0,b=1;for(int i=0;i<n;i++){int t=a+b;a=b;b=t;}return a;} }",
        "js": "var fib=function(n){let a=0,b=1;for(let i=0;i<n;i++){const t=a+b;a=b;b=t;}return a;};",
        "cpp": "class Solution { public: int fib(int n){int a=0,b=1;for(int i=0;i<n;i++){int t=a+b;a=b;b=t;}return a;} };",
    },
    "Pascal's Triangle": {
        "java": "class Solution { public java.util.List<java.util.List<Integer>> generate(int numRows){java.util.List<java.util.List<Integer>> res=new java.util.ArrayList<>();for(int i=0;i<numRows;i++){java.util.List<Integer> row=new java.util.ArrayList<>();for(int j=0;j<=i;j++){if(j==0||j==i)row.add(1);else row.add(res.get(i-1).get(j-1)+res.get(i-1).get(j));}res.add(row);}return res;} }",
        "js": "var generate=function(numRows){const res=[];for(let i=0;i<numRows;i++){const row=[];for(let j=0;j<=i;j++){if(j===0||j===i)row.push(1);else row.push(res[i-1][j-1]+res[i-1][j]);}res.push(row);}return res;};",
        "cpp": "class Solution { public: vector<vector<int>> generate(int numRows){vector<vector<int>> res;for(int i=0;i<numRows;i++){vector<int> row(i+1,1);for(int j=1;j<i;j++)row[j]=res[i-1][j-1]+res[i-1][j];res.push_back(row);}return res;} };",
    },
    "Pascal's Triangle II": {
        "java": "class Solution { public java.util.List<Integer> getRow(int rowIndex){java.util.List<Integer> row=new java.util.ArrayList<>();row.add(1);for(int i=1;i<=rowIndex;i++){for(int j=row.size()-1;j>=1;j--)row.set(j,row.get(j)+row.get(j-1));row.add(1);}return row;} }",
        "js": "var getRow=function(rowIndex){let row=[1];for(let i=1;i<=rowIndex;i++){for(let j=row.length-1;j>=1;j--)row[j]+=row[j-1];row.push(1);}return row;};",
        "cpp": "class Solution { public: vector<int> getRow(int rowIndex){vector<int> row(rowIndex+1,1);for(int i=1;i<=rowIndex;i++)for(int j=i-1;j>=1;j--)row[j]+=row[j-1];return row;} };",
    },
    "Maximum Depth of Binary Tree": {
        "java": "class Solution { public int maxDepth(TreeNode root){if(root==null)return 0;return 1+Math.max(maxDepth(root.left),maxDepth(root.right));} }",
        "js": "var maxDepth=function(root){if(!root)return 0;return 1+Math.max(maxDepth(root.left),maxDepth(root.right));};",
        "cpp": "class Solution { public: int maxDepth(TreeNode* root){if(!root)return 0;return 1+max(maxDepth(root->left),maxDepth(root->right));} };",
    },
    "Invert Binary Tree": {
        "java": "class Solution { public TreeNode invertTree(TreeNode root){if(root!=null){TreeNode l=invertTree(root.left);root.left=invertTree(root.right);root.right=l;}return root;} }",
        "js": "var invertTree=function(root){if(root){const l=invertTree(root.left);root.left=invertTree(root.right);root.right=l;}return root;};",
        "cpp": "class Solution { public: TreeNode* invertTree(TreeNode* root){if(root){TreeNode* l=invertTree(root->left);root->left=invertTree(root->right);root->right=l;}return root;} };",
    },
    "Same Tree": {
        "java": "class Solution { public boolean isSameTree(TreeNode p,TreeNode q){if(p==null&&q==null)return true;if(p==null||q==null||p.val!=q.val)return false;return isSameTree(p.left,q.left)&&isSameTree(p.right,q.right);} }",
        "js": "var isSameTree=function(p,q){if(!p&&!q)return true;if(!p||!q||p.val!==q.val)return false;return isSameTree(p.left,q.left)&&isSameTree(p.right,q.right);};",
        "cpp": "class Solution { public: bool isSameTree(TreeNode* p,TreeNode* q){if(!p&&!q)return true;if(!p||!q||p->val!=q->val)return false;return isSameTree(p->left,q->left)&&isSameTree(p->right,q->right);} };",
    },
    "Symmetric Tree": {
        "java": "class Solution { public boolean isSymmetric(TreeNode root){return root==null||mir(root.left,root.right);} boolean mir(TreeNode a,TreeNode b){if(a==null&&b==null)return true;if(a==null||b==null||a.val!=b.val)return false;return mir(a.left,b.right)&&mir(a.right,b.left);} }",
        "js": "var isSymmetric=function(root){function mir(a,b){if(!a&&!b)return true;if(!a||!b||a.val!==b.val)return false;return mir(a.left,b.right)&&mir(a.right,b.left);}return !root||mir(root.left,root.right);};",
        "cpp": "class Solution { public: bool mir(TreeNode* a,TreeNode* b){if(!a&&!b)return true;if(!a||!b||a->val!=b->val)return false;return mir(a->left,b->right)&&mir(a->right,b->left);} bool isSymmetric(TreeNode* root){return !root||mir(root->left,root->right);} };",
    },
    "Diameter of Binary Tree": {
        "java": "class Solution { int best=0; public int diameterOfBinaryTree(TreeNode root){h(root);return best;} int h(TreeNode n){if(n==null)return 0;int l=h(n.left),r=h(n.right);best=Math.max(best,l+r);return 1+Math.max(l,r);} }",
        "js": "var diameterOfBinaryTree=function(root){let best=0;function h(n){if(!n)return 0;const l=h(n.left),r=h(n.right);best=Math.max(best,l+r);return 1+Math.max(l,r);}h(root);return best;};",
        "cpp": "class Solution { public: int best=0; int h(TreeNode* n){if(!n)return 0;int l=h(n->left),r=h(n->right);best=max(best,l+r);return 1+max(l,r);} int diameterOfBinaryTree(TreeNode* root){h(root);return best;} };",
    },
    "Balanced Binary Tree": {
        "java": "class Solution { public boolean isBalanced(TreeNode root){return h(root)>=0;} int h(TreeNode n){if(n==null)return 0;int l=h(n.left);if(l<0)return -1;int r=h(n.right);if(r<0||Math.abs(l-r)>1)return -1;return 1+Math.max(l,r);} }",
        "js": "var isBalanced=function(root){function h(n){if(!n)return 0;const l=h(n.left);if(l<0)return -1;const r=h(n.right);if(r<0||Math.abs(l-r)>1)return -1;return 1+Math.max(l,r);}return h(root)>=0;};",
        "cpp": "class Solution { public: int h(TreeNode* n){if(!n)return 0;int l=h(n->left);if(l<0)return -1;int r=h(n->right);if(r<0||abs(l-r)>1)return -1;return 1+max(l,r);} bool isBalanced(TreeNode* root){return h(root)>=0;} };",
    },
    "Path Sum": {
        "java": "class Solution { public boolean hasPathSum(TreeNode root,int t){if(root==null)return false;if(root.left==null&&root.right==null)return root.val==t;int r=t-root.val;return hasPathSum(root.left,r)||hasPathSum(root.right,r);} }",
        "js": "var hasPathSum=function(root,t){if(!root)return false;if(!root.left&&!root.right)return root.val===t;const r=t-root.val;return hasPathSum(root.left,r)||hasPathSum(root.right,r);};",
        "cpp": "class Solution { public: bool hasPathSum(TreeNode* root,int t){if(!root)return false;if(!root->left&&!root->right)return root->val==t;int r=t-root->val;return hasPathSum(root->left,r)||hasPathSum(root->right,r);} };",
    },
    "Binary Tree Inorder Traversal": {
        "java": "class Solution { public int[] inorderTraversal(TreeNode root){java.util.List<Integer> r=new java.util.ArrayList<>();java.util.Deque<TreeNode> st=new java.util.ArrayDeque<>();TreeNode cur=root;while(cur!=null||!st.isEmpty()){while(cur!=null){st.push(cur);cur=cur.left;}cur=st.pop();r.add(cur.val);cur=cur.right;}int[] a=new int[r.size()];for(int i=0;i<a.length;i++)a[i]=r.get(i);return a;} }",
        "js": "var inorderTraversal=function(root){const r=[],st=[];let cur=root;while(cur||st.length){while(cur){st.push(cur);cur=cur.left;}cur=st.pop();r.push(cur.val);cur=cur.right;}return r;};",
        "cpp": "class Solution { public: vector<int> inorderTraversal(TreeNode* root){vector<int> r;stack<TreeNode*> st;TreeNode* cur=root;while(cur||!st.empty()){while(cur){st.push(cur);cur=cur->left;}cur=st.top();st.pop();r.push_back(cur->val);cur=cur->right;}return r;} };",
    },
    "Reverse Linked List": {
        "java": "class Solution { public ListNode reverseList(ListNode head){ListNode prev=null;while(head!=null){ListNode nx=head.next;head.next=prev;prev=head;head=nx;}return prev;} }",
        "js": "var reverseList=function(head){let prev=null;while(head){const nx=head.next;head.next=prev;prev=head;head=nx;}return prev;};",
        "cpp": "class Solution { public: ListNode* reverseList(ListNode* head){ListNode* prev=nullptr;while(head){ListNode* nx=head->next;head->next=prev;prev=head;head=nx;}return prev;} };",
    },
    "Merge Two Sorted Lists": {
        "java": "class Solution { public ListNode mergeTwoLists(ListNode a,ListNode b){ListNode d=new ListNode(0),t=d;while(a!=null&&b!=null){if(a.val<=b.val){t.next=a;a=a.next;}else{t.next=b;b=b.next;}t=t.next;}t.next=(a!=null)?a:b;return d.next;} }",
        "js": "var mergeTwoLists=function(a,b){const d=new ListNode(0);let t=d;while(a&&b){if(a.val<=b.val){t.next=a;a=a.next;}else{t.next=b;b=b.next;}t=t.next;}t.next=a||b;return d.next;};",
        "cpp": "class Solution { public: ListNode* mergeTwoLists(ListNode* a,ListNode* b){ListNode d(0);ListNode* t=&d;while(a&&b){if(a->val<=b->val){t->next=a;a=a->next;}else{t->next=b;b=b->next;}t=t->next;}t->next=a?a:b;return d.next;} };",
    },
    "Middle of the Linked List": {
        "java": "class Solution { public ListNode middleNode(ListNode head){ListNode s=head,f=head;while(f!=null&&f.next!=null){s=s.next;f=f.next.next;}return s;} }",
        "js": "var middleNode=function(head){let s=head,f=head;while(f&&f.next){s=s.next;f=f.next.next;}return s;};",
        "cpp": "class Solution { public: ListNode* middleNode(ListNode* head){ListNode* s=head;ListNode* f=head;while(f&&f->next){s=s->next;f=f->next->next;}return s;} };",
    },
    "Palindrome Linked List": {
        "java": "class Solution { public boolean isPalindrome(ListNode head){java.util.List<Integer> v=new java.util.ArrayList<>();while(head!=null){v.add(head.val);head=head.next;}int i=0,j=v.size()-1;while(i<j){if(!v.get(i).equals(v.get(j)))return false;i++;j--;}return true;} }",
        "js": "var isPalindrome=function(head){const v=[];while(head){v.push(head.val);head=head.next;}let i=0,j=v.length-1;while(i<j){if(v[i]!==v[j])return false;i++;j--;}return true;};",
        "cpp": "class Solution { public: bool isPalindrome(ListNode* head){vector<int> v;while(head){v.push_back(head->val);head=head->next;}int i=0,j=v.size()-1;while(i<j){if(v[i]!=v[j])return false;i++;j--;}return true;} };",
    },
    "Remove Duplicates from Sorted List": {
        "java": "class Solution { public ListNode deleteDuplicates(ListNode head){ListNode cur=head;while(cur!=null&&cur.next!=null){if(cur.next.val==cur.val)cur.next=cur.next.next;else cur=cur.next;}return head;} }",
        "js": "var deleteDuplicates=function(head){let cur=head;while(cur&&cur.next){if(cur.next.val===cur.val)cur.next=cur.next.next;else cur=cur.next;}return head;};",
        "cpp": "class Solution { public: ListNode* deleteDuplicates(ListNode* head){ListNode* cur=head;while(cur&&cur->next){if(cur->next->val==cur->val)cur->next=cur->next->next;else cur=cur->next;}return head;} };",
    },
    "Is Subsequence": {
        "java": "class Solution { public boolean isSubsequence(String s,String t){int i=0;for(int j=0;j<t.length()&&i<s.length();j++)if(s.charAt(i)==t.charAt(j))i++;return i==s.length();} }",
        "js": "var isSubsequence=function(s,t){let i=0;for(let j=0;j<t.length&&i<s.length;j++)if(s[i]===t[j])i++;return i===s.length;};",
        "cpp": "class Solution { public: bool isSubsequence(string s,string t){size_t i=0;for(size_t j=0;j<t.size()&&i<s.size();j++)if(s[i]==t[j])i++;return i==s.size();} };",
    },
    "Longest Common Prefix": {
        "java": "class Solution { public String longestCommonPrefix(String[] strs){if(strs.length==0)return \"\";String p=strs[0];for(String s:strs){while(!s.startsWith(p)){p=p.substring(0,p.length()-1);if(p.isEmpty())return \"\";}}return p;} }",
        "js": "var longestCommonPrefix=function(strs){if(strs.length===0)return '';let p=strs[0];for(const s of strs){while(s.indexOf(p)!==0){p=p.slice(0,-1);if(p==='')return '';}}return p;};",
        "cpp": "class Solution { public: string longestCommonPrefix(vector<string>& strs){if(strs.empty())return \"\";string p=strs[0];for(auto& s:strs){while(s.compare(0,p.size(),p)!=0){p=p.substr(0,p.size()-1);if(p.empty())return \"\";}}return p;} };",
    },
    "Merge Strings Alternately": {
        "java": "class Solution { public String mergeAlternately(String a,String b){StringBuilder sb=new StringBuilder();int i=0;while(i<a.length()||i<b.length()){if(i<a.length())sb.append(a.charAt(i));if(i<b.length())sb.append(b.charAt(i));i++;}return sb.toString();} }",
        "js": "var mergeAlternately=function(a,b){let r='';let i=0;while(i<a.length||i<b.length){if(i<a.length)r+=a[i];if(i<b.length)r+=b[i];i++;}return r;};",
        "cpp": "class Solution { public: string mergeAlternately(string a,string b){string r;size_t i=0;while(i<a.size()||i<b.size()){if(i<a.size())r+=a[i];if(i<b.size())r+=b[i];i++;}return r;} };",
    },
    "Find the Index of the First Occurrence in a String": {
        "java": "class Solution { public int strStr(String h,String n){return h.indexOf(n);} }",
        "js": "var strStr=function(h,n){return h.indexOf(n);};",
        "cpp": "class Solution { public: int strStr(string h,string n){auto p=h.find(n);return p==string::npos?-1:(int)p;} };",
    },
    "First Unique Character in a String": {
        "java": "class Solution { public int firstUniqChar(String s){int[] c=new int[26];for(char ch:s.toCharArray())c[ch-'a']++;for(int i=0;i<s.length();i++)if(c[s.charAt(i)-'a']==1)return i;return -1;} }",
        "js": "var firstUniqChar=function(s){const c={};for(const ch of s)c[ch]=(c[ch]||0)+1;for(let i=0;i<s.length;i++)if(c[s[i]]===1)return i;return -1;};",
        "cpp": "class Solution { public: int firstUniqChar(string s){int c[26]={0};for(char ch:s)c[ch-'a']++;for(int i=0;i<(int)s.size();i++)if(c[s[i]-'a']==1)return i;return -1;} };",
    },
    "Ransom Note": {
        "java": "class Solution { public boolean canConstruct(String r,String m){int[] c=new int[26];for(char ch:m.toCharArray())c[ch-'a']++;for(char ch:r.toCharArray())if(--c[ch-'a']<0)return false;return true;} }",
        "js": "var canConstruct=function(r,m){const c={};for(const ch of m)c[ch]=(c[ch]||0)+1;for(const ch of r){if(!c[ch])return false;c[ch]--;}return true;};",
        "cpp": "class Solution { public: bool canConstruct(string r,string m){int c[26]={0};for(char ch:m)c[ch-'a']++;for(char ch:r)if(--c[ch-'a']<0)return false;return true;} };",
    },
    "Running Sum of 1d Array": {
        "java": "class Solution { public int[] runningSum(int[] nums){int[] r=new int[nums.length];int s=0;for(int i=0;i<nums.length;i++){s+=nums[i];r[i]=s;}return r;} }",
        "js": "var runningSum=function(nums){const r=[];let s=0;for(const v of nums){s+=v;r.push(s);}return r;};",
        "cpp": "class Solution { public: vector<int> runningSum(vector<int>& nums){vector<int> r;int s=0;for(int v:nums){s+=v;r.push_back(s);}return r;} };",
    },
    "Find Pivot Index": {
        "java": "class Solution { public int pivotIndex(int[] nums){int total=0;for(int v:nums)total+=v;int left=0;for(int i=0;i<nums.length;i++){if(left==total-left-nums[i])return i;left+=nums[i];}return -1;} }",
        "js": "var pivotIndex=function(nums){let total=nums.reduce((a,b)=>a+b,0),left=0;for(let i=0;i<nums.length;i++){if(left===total-left-nums[i])return i;left+=nums[i];}return -1;};",
        "cpp": "class Solution { public: int pivotIndex(vector<int>& nums){int total=0;for(int v:nums)total+=v;int left=0;for(int i=0;i<(int)nums.size();i++){if(left==total-left-nums[i])return i;left+=nums[i];}return -1;} };",
    },
    "How Many Numbers Are Smaller Than the Current Number": {
        "java": "class Solution { public int[] smallerNumbersThanCurrent(int[] nums){int[] cnt=new int[101];for(int v:nums)cnt[v]++;int[] less=new int[101];for(int i=1;i<=100;i++)less[i]=less[i-1]+cnt[i-1];int[] r=new int[nums.length];for(int i=0;i<nums.length;i++)r[i]=less[nums[i]];return r;} }",
        "js": "var smallerNumbersThanCurrent=function(nums){const cnt=new Array(101).fill(0);for(const v of nums)cnt[v]++;const less=new Array(101).fill(0);for(let i=1;i<=100;i++)less[i]=less[i-1]+cnt[i-1];return nums.map(v=>less[v]);};",
        "cpp": "class Solution { public: vector<int> smallerNumbersThanCurrent(vector<int>& nums){int cnt[101]={0};for(int v:nums)cnt[v]++;int less[101]={0};for(int i=1;i<=100;i++)less[i]=less[i-1]+cnt[i-1];vector<int> r;for(int v:nums)r.push_back(less[v]);return r;} };",
    },
    "Last Stone Weight": {
        "java": "class Solution { public int lastStoneWeight(int[] stones){java.util.PriorityQueue<Integer> pq=new java.util.PriorityQueue<>(java.util.Collections.reverseOrder());for(int s:stones)pq.add(s);while(pq.size()>1){int a=pq.poll(),b=pq.poll();if(a!=b)pq.add(a-b);}return pq.isEmpty()?0:pq.peek();} }",
        "js": "var lastStoneWeight=function(stones){stones=stones.slice();while(stones.length>1){stones.sort((x,y)=>x-y);const a=stones.pop(),b=stones.pop();if(a!==b)stones.push(a-b);}return stones.length?stones[0]:0;};",
        "cpp": "class Solution { public: int lastStoneWeight(vector<int>& stones){priority_queue<int> pq(stones.begin(),stones.end());while(pq.size()>1){int a=pq.top();pq.pop();int b=pq.top();pq.pop();if(a!=b)pq.push(a-b);}return pq.empty()?0:pq.top();} };",
    },
    "Roman to Integer": {
        "java": "class Solution { public int romanToInt(String s){java.util.Map<Character,Integer> m=new java.util.HashMap<>();m.put('I',1);m.put('V',5);m.put('X',10);m.put('L',50);m.put('C',100);m.put('D',500);m.put('M',1000);int t=0;for(int i=0;i<s.length();i++){int v=m.get(s.charAt(i));if(i+1<s.length()&&v<m.get(s.charAt(i+1)))t-=v;else t+=v;}return t;} }",
        "js": "var romanToInt=function(s){const m={I:1,V:5,X:10,L:50,C:100,D:500,M:1000};let t=0;for(let i=0;i<s.length;i++){const v=m[s[i]];if(i+1<s.length&&v<m[s[i+1]])t-=v;else t+=v;}return t;};",
        "cpp": "class Solution { public: int romanToInt(string s){unordered_map<char,int> m{{'I',1},{'V',5},{'X',10},{'L',50},{'C',100},{'D',500},{'M',1000}};int t=0;for(size_t i=0;i<s.size();i++){int v=m[s[i]];if(i+1<s.size()&&v<m[s[i+1]])t-=v;else t+=v;}return t;} };",
    },
    "Length of Last Word": {
        "java": "class Solution { public int lengthOfLastWord(String s){int i=s.length()-1;while(i>=0&&s.charAt(i)==' ')i--;int e=i;while(i>=0&&s.charAt(i)!=' ')i--;return e-i;} }",
        "js": "var lengthOfLastWord=function(s){let i=s.length-1;while(i>=0&&s[i]===' ')i--;let e=i;while(i>=0&&s[i]!==' ')i--;return e-i;};",
        "cpp": "class Solution { public: int lengthOfLastWord(string s){int i=s.size()-1;while(i>=0&&s[i]==' ')i--;int e=i;while(i>=0&&s[i]!=' ')i--;return e-i;} };",
    },
})

# ════════════════════════ MEDIUM ════════════════════════
SOLUTIONS.update({

    "3Sum": {
        "java": "class Solution { public List<List<Integer>> threeSum(int[] nums){Arrays.sort(nums);int n=nums.length;List<List<Integer>> res=new ArrayList<>();for(int i=0;i<n;i++){if(i>0&&nums[i]==nums[i-1])continue;int l=i+1,r=n-1;while(l<r){int s=nums[i]+nums[l]+nums[r];if(s<0)l++;else if(s>0)r--;else{res.add(Arrays.asList(nums[i],nums[l],nums[r]));l++;r--;while(l<r&&nums[l]==nums[l-1])l++;while(l<r&&nums[r]==nums[r+1])r--;}}}return res;} }",
        "js": "var threeSum=function(nums){nums.sort((a,b)=>a-b);const n=nums.length,res=[];for(let i=0;i<n;i++){if(i>0&&nums[i]===nums[i-1])continue;let l=i+1,r=n-1;while(l<r){const s=nums[i]+nums[l]+nums[r];if(s<0)l++;else if(s>0)r--;else{res.push([nums[i],nums[l],nums[r]]);l++;r--;while(l<r&&nums[l]===nums[l-1])l++;while(l<r&&nums[r]===nums[r+1])r--;}}}return res;};",
        "cpp": "class Solution { public: vector<vector<int>> threeSum(vector<int>& nums){sort(nums.begin(),nums.end());int n=nums.size();vector<vector<int>> res;for(int i=0;i<n;i++){if(i>0&&nums[i]==nums[i-1])continue;int l=i+1,r=n-1;while(l<r){int s=nums[i]+nums[l]+nums[r];if(s<0)l++;else if(s>0)r--;else{res.push_back({nums[i],nums[l],nums[r]});l++;r--;while(l<r&&nums[l]==nums[l-1])l++;while(l<r&&nums[r]==nums[r+1])r--;}}}return res;} };",
    },
    "Find the Duplicate Number": {
        "java": "class Solution { public int findDuplicate(int[] nums){int s=nums[0],f=nums[0];do{s=nums[s];f=nums[nums[f]];}while(s!=f);s=nums[0];while(s!=f){s=nums[s];f=nums[f];}return s;} }",
        "js": "var findDuplicate=function(nums){let s=nums[0],f=nums[0];do{s=nums[s];f=nums[nums[f]];}while(s!==f);s=nums[0];while(s!==f){s=nums[s];f=nums[f];}return s;};",
        "cpp": "class Solution { public: int findDuplicate(vector<int>& nums){int s=nums[0],f=nums[0];do{s=nums[s];f=nums[nums[f]];}while(s!=f);s=nums[0];while(s!=f){s=nums[s];f=nums[f];}return s;} };",
    },
    "Subarray Sum Equals K": {
        "java": "class Solution { public int subarraySum(int[] nums,int k){Map<Integer,Integer> m=new HashMap<>();m.put(0,1);int cur=0,ans=0;for(int v:nums){cur+=v;ans+=m.getOrDefault(cur-k,0);m.put(cur,m.getOrDefault(cur,0)+1);}return ans;} }",
        "js": "var subarraySum=function(nums,k){const m=new Map();m.set(0,1);let cur=0,ans=0;for(const v of nums){cur+=v;ans+=m.get(cur-k)||0;m.set(cur,(m.get(cur)||0)+1);}return ans;};",
        "cpp": "class Solution { public: int subarraySum(vector<int>& nums,int k){unordered_map<int,int> m;m[0]=1;int cur=0,ans=0;for(int v:nums){cur+=v;ans+=m.count(cur-k)?m[cur-k]:0;m[cur]++;}return ans;} };",
    },
    "Find All Numbers Disappeared in an Array": {
        "java": "class Solution { public List<Integer> findDisappearedNumbers(int[] nums){boolean[] seen=new boolean[nums.length+1];for(int v:nums)seen[v]=true;List<Integer> r=new ArrayList<>();for(int i=1;i<=nums.length;i++)if(!seen[i])r.add(i);return r;} }",
        "js": "var findDisappearedNumbers=function(nums){const seen=new Array(nums.length+1).fill(false);for(const v of nums)seen[v]=true;const r=[];for(let i=1;i<=nums.length;i++)if(!seen[i])r.push(i);return r;};",
        "cpp": "class Solution { public: vector<int> findDisappearedNumbers(vector<int>& nums){vector<bool> seen(nums.size()+1,false);for(int v:nums)seen[v]=true;vector<int> r;for(int i=1;i<=(int)nums.size();i++)if(!seen[i])r.push_back(i);return r;} };",
    },
    "Maximum Product Subarray": {
        "java": "class Solution { public int maxProduct(int[] nums){int res=nums[0],mx=nums[0],mn=nums[0];for(int i=1;i<nums.length;i++){int v=nums[i];int a=mx*v,b=mn*v;mx=Math.max(v,Math.max(a,b));mn=Math.min(v,Math.min(a,b));res=Math.max(res,mx);}return res;} }",
        "js": "var maxProduct=function(nums){let res=nums[0],mx=nums[0],mn=nums[0];for(let i=1;i<nums.length;i++){const v=nums[i],a=mx*v,b=mn*v;mx=Math.max(v,a,b);mn=Math.min(v,a,b);res=Math.max(res,mx);}return res;};",
        "cpp": "class Solution { public: int maxProduct(vector<int>& nums){int res=nums[0],mx=nums[0],mn=nums[0];for(size_t i=1;i<nums.size();i++){int v=nums[i],a=mx*v,b=mn*v;mx=max(v,max(a,b));mn=min(v,min(a,b));res=max(res,mx);}return res;} };",
    },
    "Find Minimum in Rotated Sorted Array": {
        "java": "class Solution { public int findMin(int[] nums){int l=0,r=nums.length-1;while(l<r){int m=(l+r)/2;if(nums[m]>nums[r])l=m+1;else r=m;}return nums[l];} }",
        "js": "var findMin=function(nums){let l=0,r=nums.length-1;while(l<r){const m=(l+r)>>1;if(nums[m]>nums[r])l=m+1;else r=m;}return nums[l];};",
        "cpp": "class Solution { public: int findMin(vector<int>& nums){int l=0,r=nums.size()-1;while(l<r){int m=(l+r)/2;if(nums[m]>nums[r])l=m+1;else r=m;}return nums[l];} };",
    },
    "Merge Intervals": {
        "java": "class Solution { public int[][] merge(int[][] iv){Arrays.sort(iv,(a,b)->Integer.compare(a[0],b[0]));List<int[]> res=new ArrayList<>();for(int[] x:iv){if(!res.isEmpty()&&x[0]<=res.get(res.size()-1)[1])res.get(res.size()-1)[1]=Math.max(res.get(res.size()-1)[1],x[1]);else res.add(new int[]{x[0],x[1]});}return res.toArray(new int[0][]);} }",
        "js": "var merge=function(iv){iv.sort((a,b)=>a[0]-b[0]);const res=[];for(const x of iv){if(res.length&&x[0]<=res[res.length-1][1])res[res.length-1][1]=Math.max(res[res.length-1][1],x[1]);else res.push([x[0],x[1]]);}return res;};",
        "cpp": "class Solution { public: vector<vector<int>> merge(vector<vector<int>>& iv){sort(iv.begin(),iv.end());vector<vector<int>> res;for(auto& x:iv){if(!res.empty()&&x[0]<=res.back()[1])res.back()[1]=max(res.back()[1],x[1]);else res.push_back(x);}return res;} };",
    },
    "Insert Interval": {
        "java": "class Solution { public int[][] insert(int[][] iv,int[] ni){List<int[]> res=new ArrayList<>();int i=0,n=iv.length;while(i<n&&iv[i][1]<ni[0])res.add(iv[i++]);int s=ni[0],e=ni[1];while(i<n&&iv[i][0]<=e){s=Math.min(s,iv[i][0]);e=Math.max(e,iv[i][1]);i++;}res.add(new int[]{s,e});while(i<n)res.add(iv[i++]);return res.toArray(new int[0][]);} }",
        "js": "var insert=function(iv,ni){const res=[];let i=0,n=iv.length;while(i<n&&iv[i][1]<ni[0])res.push(iv[i++]);let s=ni[0],e=ni[1];while(i<n&&iv[i][0]<=e){s=Math.min(s,iv[i][0]);e=Math.max(e,iv[i][1]);i++;}res.push([s,e]);while(i<n)res.push(iv[i++]);return res;};",
        "cpp": "class Solution { public: vector<vector<int>> insert(vector<vector<int>>& iv,vector<int>& ni){vector<vector<int>> res;int i=0,n=iv.size();while(i<n&&iv[i][1]<ni[0])res.push_back(iv[i++]);int s=ni[0],e=ni[1];while(i<n&&iv[i][0]<=e){s=min(s,iv[i][0]);e=max(e,iv[i][1]);i++;}res.push_back({s,e});while(i<n)res.push_back(iv[i++]);return res;} };",
    },
    "Non-overlapping Intervals": {
        "java": "class Solution { public int eraseOverlapIntervals(int[][] iv){Arrays.sort(iv,(a,b)->Integer.compare(a[1],b[1]));long prev=Long.MIN_VALUE;int keep=0;for(int[] x:iv){if(x[0]>=prev){keep++;prev=x[1];}}return iv.length-keep;} }",
        "js": "var eraseOverlapIntervals=function(iv){iv.sort((a,b)=>a[1]-b[1]);let prev=-Infinity,keep=0;for(const x of iv){if(x[0]>=prev){keep++;prev=x[1];}}return iv.length-keep;};",
        "cpp": "class Solution { public: int eraseOverlapIntervals(vector<vector<int>>& iv){sort(iv.begin(),iv.end(),[](const vector<int>&a,const vector<int>&b){return a[1]<b[1];});long long prev=LLONG_MIN;int keep=0;for(auto& x:iv){if(x[0]>=prev){keep++;prev=x[1];}}return (int)iv.size()-keep;} };",
    },
    "Sort Colors": {
        "java": "class Solution { public void sortColors(int[] nums){int lo=0,i=0,hi=nums.length-1;while(i<=hi){if(nums[i]==0){int t=nums[lo];nums[lo]=nums[i];nums[i]=t;lo++;i++;}else if(nums[i]==2){int t=nums[hi];nums[hi]=nums[i];nums[i]=t;hi--;}else i++;}} }",
        "js": "var sortColors=function(nums){let lo=0,i=0,hi=nums.length-1;while(i<=hi){if(nums[i]===0){[nums[lo],nums[i]]=[nums[i],nums[lo]];lo++;i++;}else if(nums[i]===2){[nums[hi],nums[i]]=[nums[i],nums[hi]];hi--;}else i++;}};",
        "cpp": "class Solution { public: void sortColors(vector<int>& nums){int lo=0,i=0,hi=nums.size()-1;while(i<=hi){if(nums[i]==0){swap(nums[lo],nums[i]);lo++;i++;}else if(nums[i]==2){swap(nums[hi],nums[i]);hi--;}else i++;}} };",
    },
    "Rotate Image": {
        "java": "class Solution { public void rotate(int[][] m){int n=m.length;for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){int t=m[i][j];m[i][j]=m[j][i];m[j][i]=t;}for(int i=0;i<n;i++)for(int l=0,r=n-1;l<r;l++,r--){int t=m[i][l];m[i][l]=m[i][r];m[i][r]=t;}} }",
        "js": "var rotate=function(m){let n=m.length;for(let i=0;i<n;i++)for(let j=i+1;j<n;j++){const t=m[i][j];m[i][j]=m[j][i];m[j][i]=t;}for(let i=0;i<n;i++)m[i].reverse();};",
        "cpp": "class Solution { public: void rotate(vector<vector<int>>& m){int n=m.size();for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)swap(m[i][j],m[j][i]);for(int i=0;i<n;i++)reverse(m[i].begin(),m[i].end());} };",
    },
    "Spiral Matrix": {
        "java": "class Solution { public List<Integer> spiralOrder(int[][] m){List<Integer> r=new ArrayList<>();if(m.length==0)return r;int top=0,bot=m.length-1,left=0,right=m[0].length-1;while(top<=bot&&left<=right){for(int j=left;j<=right;j++)r.add(m[top][j]);top++;for(int i=top;i<=bot;i++)r.add(m[i][right]);right--;if(top<=bot){for(int j=right;j>=left;j--)r.add(m[bot][j]);bot--;}if(left<=right){for(int i=bot;i>=top;i--)r.add(m[i][left]);left++;}}return r;} }",
        "js": "var spiralOrder=function(m){const r=[];if(m.length===0)return r;let top=0,bot=m.length-1,left=0,right=m[0].length-1;while(top<=bot&&left<=right){for(let j=left;j<=right;j++)r.push(m[top][j]);top++;for(let i=top;i<=bot;i++)r.push(m[i][right]);right--;if(top<=bot){for(let j=right;j>=left;j--)r.push(m[bot][j]);bot--;}if(left<=right){for(let i=bot;i>=top;i--)r.push(m[i][left]);left++;}}return r;};",
        "cpp": "class Solution { public: vector<int> spiralOrder(vector<vector<int>>& m){vector<int> r;if(m.empty())return r;int top=0,bot=m.size()-1,left=0,right=m[0].size()-1;while(top<=bot&&left<=right){for(int j=left;j<=right;j++)r.push_back(m[top][j]);top++;for(int i=top;i<=bot;i++)r.push_back(m[i][right]);right--;if(top<=bot){for(int j=right;j>=left;j--)r.push_back(m[bot][j]);bot--;}if(left<=right){for(int i=bot;i>=top;i--)r.push_back(m[i][left]);left++;}}return r;} };",
    },
    "Set Matrix Zeroes": {
        "java": "class Solution { public void setZeroes(int[][] m){Set<Integer> rows=new HashSet<>(),cols=new HashSet<>();for(int i=0;i<m.length;i++)for(int j=0;j<m[0].length;j++)if(m[i][j]==0){rows.add(i);cols.add(j);}for(int i=0;i<m.length;i++)for(int j=0;j<m[0].length;j++)if(rows.contains(i)||cols.contains(j))m[i][j]=0;} }",
        "js": "var setZeroes=function(m){const rows=new Set(),cols=new Set();for(let i=0;i<m.length;i++)for(let j=0;j<m[0].length;j++)if(m[i][j]===0){rows.add(i);cols.add(j);}for(let i=0;i<m.length;i++)for(let j=0;j<m[0].length;j++)if(rows.has(i)||cols.has(j))m[i][j]=0;};",
        "cpp": "class Solution { public: void setZeroes(vector<vector<int>>& m){set<int> rows,cols;for(int i=0;i<(int)m.size();i++)for(int j=0;j<(int)m[0].size();j++)if(m[i][j]==0){rows.insert(i);cols.insert(j);}for(int i=0;i<(int)m.size();i++)for(int j=0;j<(int)m[0].size();j++)if(rows.count(i)||cols.count(j))m[i][j]=0;} };",
    },
    "Search a 2D Matrix": {
        "java": "class Solution { public boolean searchMatrix(int[][] m,int target){int rows=m.length,cols=m[0].length,lo=0,hi=rows*cols-1;while(lo<=hi){int mid=(lo+hi)/2;int v=m[mid/cols][mid%cols];if(v==target)return true;if(v<target)lo=mid+1;else hi=mid-1;}return false;} }",
        "js": "var searchMatrix=function(m,target){const rows=m.length,cols=m[0].length;let lo=0,hi=rows*cols-1;while(lo<=hi){const mid=(lo+hi)>>1,v=m[Math.floor(mid/cols)][mid%cols];if(v===target)return true;if(v<target)lo=mid+1;else hi=mid-1;}return false;};",
        "cpp": "class Solution { public: bool searchMatrix(vector<vector<int>>& m,int target){int rows=m.size(),cols=m[0].size(),lo=0,hi=rows*cols-1;while(lo<=hi){int mid=(lo+hi)/2;int v=m[mid/cols][mid%cols];if(v==target)return true;if(v<target)lo=mid+1;else hi=mid-1;}return false;} };",
    },
    "Number of Islands": {
        "java": "class Solution { public int numIslands(char[][] g){int R=g.length,C=g[0].length,cnt=0;for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(g[i][j]=='1'){cnt++;dfs(g,i,j,R,C);}return cnt;} void dfs(char[][] g,int r,int c,int R,int C){if(r<0||r>=R||c<0||c>=C||g[r][c]!='1')return;g[r][c]='0';dfs(g,r+1,c,R,C);dfs(g,r-1,c,R,C);dfs(g,r,c+1,R,C);dfs(g,r,c-1,R,C);} }",
        "js": "var numIslands=function(g){let R=g.length,C=g[0].length,cnt=0;function dfs(r,c){if(r<0||r>=R||c<0||c>=C||g[r][c]!=='1')return;g[r][c]='0';dfs(r+1,c);dfs(r-1,c);dfs(r,c+1);dfs(r,c-1);}for(let i=0;i<R;i++)for(let j=0;j<C;j++)if(g[i][j]==='1'){cnt++;dfs(i,j);}return cnt;};",
        "cpp": "class Solution { public: int R,C; void dfs(vector<vector<char>>& g,int r,int c){if(r<0||r>=R||c<0||c>=C||g[r][c]!='1')return;g[r][c]='0';dfs(g,r+1,c);dfs(g,r-1,c);dfs(g,r,c+1);dfs(g,r,c-1);} int numIslands(vector<vector<char>>& g){R=g.size();C=g[0].size();int cnt=0;for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(g[i][j]=='1'){cnt++;dfs(g,i,j);}return cnt;} };",
    },
    "Rotting Oranges": {
        "java": "class Solution { public int orangesRotting(int[][] g){int R=g.length,C=g[0].length,fresh=0;Deque<int[]> q=new ArrayDeque<>();for(int i=0;i<R;i++)for(int j=0;j<C;j++){if(g[i][j]==2)q.add(new int[]{i,j,0});else if(g[i][j]==1)fresh++;}int t=0;int[][] d={{1,0},{-1,0},{0,1},{0,-1}};while(!q.isEmpty()){int[] c=q.poll();t=c[2];for(int[] dd:d){int nr=c[0]+dd[0],nc=c[1]+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&g[nr][nc]==1){g[nr][nc]=2;fresh--;q.add(new int[]{nr,nc,t+1});}}}return fresh==0?t:-1;} }",
        "js": "var orangesRotting=function(g){let R=g.length,C=g[0].length,fresh=0;const q=[];for(let i=0;i<R;i++)for(let j=0;j<C;j++){if(g[i][j]===2)q.push([i,j,0]);else if(g[i][j]===1)fresh++;}let t=0,head=0;const d=[[1,0],[-1,0],[0,1],[0,-1]];while(head<q.length){const c=q[head++];t=c[2];for(const dd of d){const nr=c[0]+dd[0],nc=c[1]+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&g[nr][nc]===1){g[nr][nc]=2;fresh--;q.push([nr,nc,t+1]);}}}return fresh===0?t:-1;};",
        "cpp": "class Solution { public: int orangesRotting(vector<vector<int>>& g){int R=g.size(),C=g[0].size(),fresh=0;queue<array<int,3>> q;for(int i=0;i<R;i++)for(int j=0;j<C;j++){if(g[i][j]==2)q.push({i,j,0});else if(g[i][j]==1)fresh++;}int t=0;int d[4][2]={{1,0},{-1,0},{0,1},{0,-1}};while(!q.empty()){auto c=q.front();q.pop();t=c[2];for(auto& dd:d){int nr=c[0]+dd[0],nc=c[1]+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&g[nr][nc]==1){g[nr][nc]=2;fresh--;q.push({nr,nc,t+1});}}}return fresh==0?t:-1;} };",
    },
    "Maximal Square": {
        "java": "class Solution { public int maximalSquare(char[][] m){int R=m.length,C=m[0].length;int[] dp=new int[C+1];int best=0,prev=0;for(int i=0;i<R;i++){prev=0;for(int j=0;j<C;j++){int tmp=dp[j+1];if(m[i][j]=='1'){dp[j+1]=Math.min(Math.min(dp[j],dp[j+1]),prev)+1;best=Math.max(best,dp[j+1]);}else dp[j+1]=0;prev=tmp;}}return best*best;} }",
        "js": "var maximalSquare=function(m){const R=m.length,C=m[0].length,dp=new Array(C+1).fill(0);let best=0;for(let i=0;i<R;i++){let prev=0;for(let j=0;j<C;j++){const tmp=dp[j+1];if(m[i][j]==='1'){dp[j+1]=Math.min(dp[j],dp[j+1],prev)+1;best=Math.max(best,dp[j+1]);}else dp[j+1]=0;prev=tmp;}}return best*best;};",
        "cpp": "class Solution { public: int maximalSquare(vector<vector<char>>& m){int R=m.size(),C=m[0].size();vector<int> dp(C+1,0);int best=0;for(int i=0;i<R;i++){int prev=0;for(int j=0;j<C;j++){int tmp=dp[j+1];if(m[i][j]=='1'){dp[j+1]=min(min(dp[j],dp[j+1]),prev)+1;best=max(best,dp[j+1]);}else dp[j+1]=0;prev=tmp;}}return best*best;} };",
    },
    "House Robber": {
        "java": "class Solution { public int rob(int[] nums){int prev=0,cur=0;for(int v:nums){int t=Math.max(cur,prev+v);prev=cur;cur=t;}return cur;} }",
        "js": "var rob=function(nums){let prev=0,cur=0;for(const v of nums){const t=Math.max(cur,prev+v);prev=cur;cur=t;}return cur;};",
        "cpp": "class Solution { public: int rob(vector<int>& nums){int prev=0,cur=0;for(int v:nums){int t=max(cur,prev+v);prev=cur;cur=t;}return cur;} };",
    },
    "House Robber II": {
        "java": "class Solution { public int rob(int[] nums){if(nums.length==1)return nums[0];return Math.max(line(nums,0,nums.length-2),line(nums,1,nums.length-1));} int line(int[] a,int lo,int hi){int prev=0,cur=0;for(int i=lo;i<=hi;i++){int t=Math.max(cur,prev+a[i]);prev=cur;cur=t;}return cur;} }",
        "js": "var rob=function(nums){if(nums.length===1)return nums[0];function line(lo,hi){let prev=0,cur=0;for(let i=lo;i<=hi;i++){const t=Math.max(cur,prev+nums[i]);prev=cur;cur=t;}return cur;}return Math.max(line(0,nums.length-2),line(1,nums.length-1));};",
        "cpp": "class Solution { public: int line(vector<int>& a,int lo,int hi){int prev=0,cur=0;for(int i=lo;i<=hi;i++){int t=max(cur,prev+a[i]);prev=cur;cur=t;}return cur;} int rob(vector<int>& nums){if(nums.size()==1)return nums[0];return max(line(nums,0,nums.size()-2),line(nums,1,nums.size()-1));} };",
    },
    "Longest Increasing Subsequence": {
        "java": "class Solution { public int lengthOfLIS(int[] nums){List<Integer> tails=new ArrayList<>();for(int v:nums){int lo=0,hi=tails.size();while(lo<hi){int m=(lo+hi)/2;if(tails.get(m)<v)lo=m+1;else hi=m;}if(lo==tails.size())tails.add(v);else tails.set(lo,v);}return tails.size();} }",
        "js": "var lengthOfLIS=function(nums){const t=[];for(const v of nums){let lo=0,hi=t.length;while(lo<hi){const m=(lo+hi)>>1;if(t[m]<v)lo=m+1;else hi=m;}if(lo===t.length)t.push(v);else t[lo]=v;}return t.length;};",
        "cpp": "class Solution { public: int lengthOfLIS(vector<int>& nums){vector<int> t;for(int v:nums){auto it=lower_bound(t.begin(),t.end(),v);if(it==t.end())t.push_back(v);else *it=v;}return t.size();} };",
    },
    "Coin Change": {
        "java": "class Solution { public int coinChange(int[] coins,int amount){int INF=amount+1;int[] dp=new int[amount+1];Arrays.fill(dp,INF);dp[0]=0;for(int a=1;a<=amount;a++)for(int c:coins)if(c<=a)dp[a]=Math.min(dp[a],dp[a-c]+1);return dp[amount]>amount?-1:dp[amount];} }",
        "js": "var coinChange=function(coins,amount){const INF=amount+1;const dp=new Array(amount+1).fill(INF);dp[0]=0;for(let a=1;a<=amount;a++)for(const c of coins)if(c<=a)dp[a]=Math.min(dp[a],dp[a-c]+1);return dp[amount]>amount?-1:dp[amount];};",
        "cpp": "class Solution { public: int coinChange(vector<int>& coins,int amount){int INF=amount+1;vector<int> dp(amount+1,INF);dp[0]=0;for(int a=1;a<=amount;a++)for(int c:coins)if(c<=a)dp[a]=min(dp[a],dp[a-c]+1);return dp[amount]>amount?-1:dp[amount];} };",
    },
    "Coin Change II": {
        "java": "class Solution { public int change(int amount,int[] coins){int[] dp=new int[amount+1];dp[0]=1;for(int c:coins)for(int a=c;a<=amount;a++)dp[a]+=dp[a-c];return dp[amount];} }",
        "js": "var change=function(amount,coins){const dp=new Array(amount+1).fill(0);dp[0]=1;for(const c of coins)for(let a=c;a<=amount;a++)dp[a]+=dp[a-c];return dp[amount];};",
        "cpp": "class Solution { public: int change(int amount,vector<int>& coins){vector<int> dp(amount+1,0);dp[0]=1;for(int c:coins)for(int a=c;a<=amount;a++)dp[a]+=dp[a-c];return dp[amount];} };",
    },
    "Min Cost Climbing Stairs": {
        "java": "class Solution { public int minCostClimbingStairs(int[] cost){int a=0,b=0;for(int i=2;i<=cost.length;i++){int c=Math.min(b+cost[i-1],a+cost[i-2]);a=b;b=c;}return b;} }",
        "js": "var minCostClimbingStairs=function(cost){let a=0,b=0;for(let i=2;i<=cost.length;i++){const c=Math.min(b+cost[i-1],a+cost[i-2]);a=b;b=c;}return b;};",
        "cpp": "class Solution { public: int minCostClimbingStairs(vector<int>& cost){int a=0,b=0;for(int i=2;i<=(int)cost.size();i++){int c=min(b+cost[i-1],a+cost[i-2]);a=b;b=c;}return b;} };",
    },
    "Decode Ways": {
        "java": "class Solution { public int numDecodings(String s){if(s.isEmpty()||s.charAt(0)=='0')return 0;int prev=1,cur=1;for(int i=1;i<s.length();i++){int tmp=0;if(s.charAt(i)!='0')tmp+=cur;int two=Integer.parseInt(s.substring(i-1,i+1));if(two>=10&&two<=26)tmp+=prev;prev=cur;cur=tmp;}return cur;} }",
        "js": "var numDecodings=function(s){if(!s||s[0]==='0')return 0;let prev=1,cur=1;for(let i=1;i<s.length;i++){let tmp=0;if(s[i]!=='0')tmp+=cur;const two=parseInt(s.substring(i-1,i+1),10);if(two>=10&&two<=26)tmp+=prev;prev=cur;cur=tmp;}return cur;};",
        "cpp": "class Solution { public: int numDecodings(string s){if(s.empty()||s[0]=='0')return 0;int prev=1,cur=1;for(size_t i=1;i<s.size();i++){int tmp=0;if(s[i]!='0')tmp+=cur;int two=stoi(s.substr(i-1,2));if(two>=10&&two<=26)tmp+=prev;prev=cur;cur=tmp;}return cur;} };",
    },
    "Unique Paths": {
        "java": "class Solution { public int uniquePaths(int m,int n){int[] dp=new int[n];Arrays.fill(dp,1);for(int i=1;i<m;i++)for(int j=1;j<n;j++)dp[j]+=dp[j-1];return dp[n-1];} }",
        "js": "var uniquePaths=function(m,n){const dp=new Array(n).fill(1);for(let i=1;i<m;i++)for(let j=1;j<n;j++)dp[j]+=dp[j-1];return dp[n-1];};",
        "cpp": "class Solution { public: int uniquePaths(int m,int n){vector<int> dp(n,1);for(int i=1;i<m;i++)for(int j=1;j<n;j++)dp[j]+=dp[j-1];return dp[n-1];} };",
    },
    "Unique Paths II": {
        "java": "class Solution { public int uniquePathsWithObstacles(int[][] g){int n=g[0].length;int[] dp=new int[n];dp[0]=1;for(int[] row:g)for(int j=0;j<n;j++){if(row[j]==1)dp[j]=0;else if(j>0)dp[j]+=dp[j-1];}return dp[n-1];} }",
        "js": "var uniquePathsWithObstacles=function(g){const n=g[0].length,dp=new Array(n).fill(0);dp[0]=1;for(const row of g)for(let j=0;j<n;j++){if(row[j]===1)dp[j]=0;else if(j>0)dp[j]+=dp[j-1];}return dp[n-1];};",
        "cpp": "class Solution { public: int uniquePathsWithObstacles(vector<vector<int>>& g){int n=g[0].size();vector<int> dp(n,0);dp[0]=1;for(auto& row:g)for(int j=0;j<n;j++){if(row[j]==1)dp[j]=0;else if(j>0)dp[j]+=dp[j-1];}return dp[n-1];} };",
    },
    "Minimum Path Sum": {
        "java": "class Solution { public int minPathSum(int[][] grid){int m=grid.length,n=grid[0].length;int[] dp=new int[n];for(int j=0;j<n;j++)dp[j]=Integer.MAX_VALUE;dp[0]=0;for(int i=0;i<m;i++){dp[0]+=grid[i][0];for(int j=1;j<n;j++)dp[j]=Math.min(dp[j],dp[j-1])+grid[i][j];}return dp[n-1];} }",
        "js": "var minPathSum=function(grid){let m=grid.length,n=grid[0].length,dp=new Array(n).fill(Infinity);dp[0]=0;for(let i=0;i<m;i++){dp[0]+=grid[i][0];for(let j=1;j<n;j++)dp[j]=Math.min(dp[j],dp[j-1])+grid[i][j];}return dp[n-1];};",
        "cpp": "class Solution { public: int minPathSum(vector<vector<int>>& grid){int m=grid.size(),n=grid[0].size();vector<int> dp(n,INT_MAX);dp[0]=0;for(int i=0;i<m;i++){dp[0]+=grid[i][0];for(int j=1;j<n;j++)dp[j]=min(dp[j],dp[j-1])+grid[i][j];}return dp[n-1];} };",
    },
    "Partition Equal Subset Sum": {
        "java": "class Solution { public boolean canPartition(int[] nums){int total=0;for(int v:nums)total+=v;if(total%2!=0)return false;int t=total/2;boolean[] dp=new boolean[t+1];dp[0]=true;for(int v:nums)for(int a=t;a>=v;a--)dp[a]=dp[a]||dp[a-v];return dp[t];} }",
        "js": "var canPartition=function(nums){let total=nums.reduce((a,b)=>a+b,0);if(total%2!==0)return false;const t=total/2,dp=new Array(t+1).fill(false);dp[0]=true;for(const v of nums)for(let a=t;a>=v;a--)dp[a]=dp[a]||dp[a-v];return dp[t];};",
        "cpp": "class Solution { public: bool canPartition(vector<int>& nums){int total=0;for(int v:nums)total+=v;if(total%2)return false;int t=total/2;vector<char> dp(t+1,0);dp[0]=1;for(int v:nums)for(int a=t;a>=v;a--)dp[a]=dp[a]||dp[a-v];return dp[t];} };",
    },
    "Word Break": {
        "java": "class Solution { public boolean wordBreak(String s,String[] wordDict){Set<String> w=new HashSet<>(Arrays.asList(wordDict));int n=s.length();boolean[] dp=new boolean[n+1];dp[0]=true;for(int i=1;i<=n;i++)for(int j=0;j<i;j++)if(dp[j]&&w.contains(s.substring(j,i))){dp[i]=true;break;}return dp[n];} }",
        "js": "var wordBreak=function(s,wordDict){const w=new Set(wordDict),n=s.length,dp=new Array(n+1).fill(false);dp[0]=true;for(let i=1;i<=n;i++)for(let j=0;j<i;j++)if(dp[j]&&w.has(s.substring(j,i))){dp[i]=true;break;}return dp[n];};",
        "cpp": "class Solution { public: bool wordBreak(string s,vector<string>& wordDict){set<string> w(wordDict.begin(),wordDict.end());int n=s.size();vector<char> dp(n+1,0);dp[0]=1;for(int i=1;i<=n;i++)for(int j=0;j<i;j++)if(dp[j]&&w.count(s.substr(j,i-j))){dp[i]=1;break;}return dp[n];} };",
    },
    "Combination Sum": {
        "java": "class Solution { public List<List<Integer>> combinationSum(int[] candidates,int target){Arrays.sort(candidates);List<List<Integer>> res=new ArrayList<>();bt(candidates,0,target,new ArrayList<>(),res);return res;} void bt(int[] c,int start,int rem,List<Integer> path,List<List<Integer>> res){if(rem==0){res.add(new ArrayList<>(path));return;}for(int i=start;i<c.length;i++){if(c[i]>rem)break;path.add(c[i]);bt(c,i,rem-c[i],path,res);path.remove(path.size()-1);}} }",
        "js": "var combinationSum=function(candidates,target){candidates.sort((a,b)=>a-b);const res=[];function bt(start,rem,path){if(rem===0){res.push(path.slice());return;}for(let i=start;i<candidates.length;i++){if(candidates[i]>rem)break;path.push(candidates[i]);bt(i,rem-candidates[i],path);path.pop();}}bt(0,target,[]);return res;};",
        "cpp": "class Solution { public: void bt(vector<int>& c,int start,int rem,vector<int>& path,vector<vector<int>>& res){if(rem==0){res.push_back(path);return;}for(int i=start;i<(int)c.size();i++){if(c[i]>rem)break;path.push_back(c[i]);bt(c,i,rem-c[i],path,res);path.pop_back();}} vector<vector<int>> combinationSum(vector<int>& candidates,int target){sort(candidates.begin(),candidates.end());vector<vector<int>> res;vector<int> path;bt(candidates,0,target,path,res);return res;} };",
    },
    "Letter Combinations of a Phone Number": {
        "java": "class Solution { public List<String> letterCombinations(String digits){List<String> res=new ArrayList<>();if(digits.isEmpty())return res;String[] m={\"\",\"\",\"abc\",\"def\",\"ghi\",\"jkl\",\"mno\",\"pqrs\",\"tuv\",\"wxyz\"};res.add(\"\");for(char d:digits.toCharArray()){List<String> nx=new ArrayList<>();for(String p:res)for(char c:m[d-'0'].toCharArray())nx.add(p+c);res=nx;}return res;} }",
        "js": "var letterCombinations=function(digits){if(digits.length===0)return [];const m={'2':'abc','3':'def','4':'ghi','5':'jkl','6':'mno','7':'pqrs','8':'tuv','9':'wxyz'};let res=[''];for(const d of digits){const nx=[];for(const p of res)for(const c of m[d])nx.push(p+c);res=nx;}return res;};",
        "cpp": "class Solution { public: vector<string> letterCombinations(string digits){if(digits.empty())return {};vector<string> m={\"\",\"\",\"abc\",\"def\",\"ghi\",\"jkl\",\"mno\",\"pqrs\",\"tuv\",\"wxyz\"};vector<string> res={\"\"};for(char d:digits){vector<string> nx;for(auto& p:res)for(char c:m[d-'0'])nx.push_back(p+c);res=nx;}return res;} };",
    },
    "Word Search": {
        "java": "class Solution { char[][] b; String w; int R,C; public boolean exist(char[][] board,String word){b=board;w=word;R=board.length;C=board[0].length;for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(dfs(i,j,0))return true;return false;} boolean dfs(int r,int c,int i){if(i==w.length())return true;if(r<0||r>=R||c<0||c>=C||b[r][c]!=w.charAt(i))return false;char t=b[r][c];b[r][c]='#';boolean f=dfs(r+1,c,i+1)||dfs(r-1,c,i+1)||dfs(r,c+1,i+1)||dfs(r,c-1,i+1);b[r][c]=t;return f;} }",
        "js": "var exist=function(board,word){const R=board.length,C=board[0].length;function dfs(r,c,i){if(i===word.length)return true;if(r<0||r>=R||c<0||c>=C||board[r][c]!==word[i])return false;const t=board[r][c];board[r][c]='#';const f=dfs(r+1,c,i+1)||dfs(r-1,c,i+1)||dfs(r,c+1,i+1)||dfs(r,c-1,i+1);board[r][c]=t;return f;}for(let i=0;i<R;i++)for(let j=0;j<C;j++)if(dfs(i,j,0))return true;return false;};",
        "cpp": "class Solution { public: vector<vector<char>>* b; string w; int R,C; bool dfs(int r,int c,int i){if(i==(int)w.size())return true;if(r<0||r>=R||c<0||c>=C||(*b)[r][c]!=w[i])return false;char t=(*b)[r][c];(*b)[r][c]='#';bool f=dfs(r+1,c,i+1)||dfs(r-1,c,i+1)||dfs(r,c+1,i+1)||dfs(r,c-1,i+1);(*b)[r][c]=t;return f;} bool exist(vector<vector<char>>& board,string word){b=&board;w=word;R=board.size();C=board[0].size();for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(dfs(i,j,0))return true;return false;} };",
    },
    "Course Schedule": {
        "java": "class Solution { public boolean canFinish(int numCourses,int[][] pre){List<List<Integer>> adj=new ArrayList<>();int[] indeg=new int[numCourses];for(int i=0;i<numCourses;i++)adj.add(new ArrayList<>());for(int[] p:pre){adj.get(p[1]).add(p[0]);indeg[p[0]]++;}Deque<Integer> q=new ArrayDeque<>();for(int i=0;i<numCourses;i++)if(indeg[i]==0)q.add(i);int done=0;while(!q.isEmpty()){int u=q.poll();done++;for(int v:adj.get(u))if(--indeg[v]==0)q.add(v);}return done==numCourses;} }",
        "js": "var canFinish=function(numCourses,pre){const adj=Array.from({length:numCourses},()=>[]),indeg=new Array(numCourses).fill(0);for(const[a,b] of pre){adj[b].push(a);indeg[a]++;}const q=[];for(let i=0;i<numCourses;i++)if(indeg[i]===0)q.push(i);let done=0,head=0;while(head<q.length){const u=q[head++];done++;for(const v of adj[u])if(--indeg[v]===0)q.push(v);}return done===numCourses;};",
        "cpp": "class Solution { public: bool canFinish(int numCourses,vector<vector<int>>& pre){vector<vector<int>> adj(numCourses);vector<int> indeg(numCourses,0);for(auto& p:pre){adj[p[1]].push_back(p[0]);indeg[p[0]]++;}queue<int> q;for(int i=0;i<numCourses;i++)if(indeg[i]==0)q.push(i);int done=0;while(!q.empty()){int u=q.front();q.pop();done++;for(int v:adj[u])if(--indeg[v]==0)q.push(v);}return done==numCourses;} };",
    },
    "Number of Connected Components in an Undirected Graph": {
        "java": "class Solution { int[] p; int find(int x){while(p[x]!=x){p[x]=p[p[x]];x=p[x];}return x;} public int countComponents(int n,int[][] edges){p=new int[n];for(int i=0;i<n;i++)p[i]=i;int comp=n;for(int[] e:edges){int a=find(e[0]),b=find(e[1]);if(a!=b){p[a]=b;comp--;}}return comp;} }",
        "js": "var countComponents=function(n,edges){const p=Array.from({length:n},(_,i)=>i);function find(x){while(p[x]!==x){p[x]=p[p[x]];x=p[x];}return x;}let comp=n;for(const[a,b] of edges){const ra=find(a),rb=find(b);if(ra!==rb){p[ra]=rb;comp--;}}return comp;};",
        "cpp": "class Solution { public: vector<int> p; int find(int x){while(p[x]!=x){p[x]=p[p[x]];x=p[x];}return x;} int countComponents(int n,vector<vector<int>>& edges){p.resize(n);for(int i=0;i<n;i++)p[i]=i;int comp=n;for(auto& e:edges){int a=find(e[0]),b=find(e[1]);if(a!=b){p[a]=b;comp--;}}return comp;} };",
    },
    "Gas Station": {
        "java": "class Solution { public int canCompleteCircuit(int[] gas,int[] cost){int tg=0,tc=0;for(int v:gas)tg+=v;for(int v:cost)tc+=v;if(tg<tc)return -1;int total=0,start=0;for(int i=0;i<gas.length;i++){total+=gas[i]-cost[i];if(total<0){start=i+1;total=0;}}return start;} }",
        "js": "var canCompleteCircuit=function(gas,cost){let tg=gas.reduce((a,b)=>a+b,0),tc=cost.reduce((a,b)=>a+b,0);if(tg<tc)return -1;let total=0,start=0;for(let i=0;i<gas.length;i++){total+=gas[i]-cost[i];if(total<0){start=i+1;total=0;}}return start;};",
        "cpp": "class Solution { public: int canCompleteCircuit(vector<int>& gas,vector<int>& cost){int tg=0,tc=0;for(int v:gas)tg+=v;for(int v:cost)tc+=v;if(tg<tc)return -1;int total=0,start=0;for(int i=0;i<(int)gas.size();i++){total+=gas[i]-cost[i];if(total<0){start=i+1;total=0;}}return start;} };",
    },
    "Hand of Straights": {
        "java": "class Solution { public boolean isNStraightHand(int[] hand,int groupSize){if(hand.length%groupSize!=0)return false;TreeMap<Integer,Integer> cnt=new TreeMap<>();for(int v:hand)cnt.merge(v,1,Integer::sum);while(!cnt.isEmpty()){int first=cnt.firstKey();int c=cnt.get(first);for(int k=first;k<first+groupSize;k++){Integer cur=cnt.get(k);if(cur==null||cur<c)return false;if(cur==c)cnt.remove(k);else cnt.put(k,cur-c);}}return true;} }",
        "js": "var isNStraightHand=function(hand,groupSize){if(hand.length%groupSize!==0)return false;const cnt=new Map();for(const v of hand)cnt.set(v,(cnt.get(v)||0)+1);const keys=[...cnt.keys()].sort((a,b)=>a-b);for(const v of keys){const c=cnt.get(v)||0;if(c>0)for(let k=v;k<v+groupSize;k++){const cur=cnt.get(k)||0;if(cur<c)return false;cnt.set(k,cur-c);}}return true;};",
        "cpp": "class Solution { public: bool isNStraightHand(vector<int>& hand,int groupSize){if(hand.size()%groupSize!=0)return false;map<int,int> cnt;for(int v:hand)cnt[v]++;for(auto& pr:cnt){int v=pr.first,c=cnt[v];if(c>0)for(int k=v;k<v+groupSize;k++){if(cnt[k]<c)return false;cnt[k]-=c;}}return true;} };",
    },
    "Evaluate Reverse Polish Notation": {
        "java": "class Solution { public int evalRPN(String[] tokens){Deque<Integer> st=new ArrayDeque<>();for(String t:tokens){if(t.equals(\"+\")||t.equals(\"-\")||t.equals(\"*\")||t.equals(\"/\")){int b=st.pop(),a=st.pop();if(t.equals(\"+\"))st.push(a+b);else if(t.equals(\"-\"))st.push(a-b);else if(t.equals(\"*\"))st.push(a*b);else st.push(a/b);}else st.push(Integer.parseInt(t));}return st.pop();} }",
        "js": "var evalRPN=function(tokens){const st=[];for(const t of tokens){if(t==='+'||t==='-'||t==='*'||t==='/'){const b=st.pop(),a=st.pop();let v;if(t==='+')v=a+b;else if(t==='-')v=a-b;else if(t==='*')v=a*b;else v=Math.trunc(a/b);st.push(v);}else st.push(parseInt(t,10));}return st[0];};",
        "cpp": "class Solution { public: int evalRPN(vector<string>& tokens){vector<int> st;for(auto& t:tokens){if(t==\"+\"||t==\"-\"||t==\"*\"||t==\"/\"){int b=st.back();st.pop_back();int a=st.back();st.pop_back();if(t==\"+\")st.push_back(a+b);else if(t==\"-\")st.push_back(a-b);else if(t==\"*\")st.push_back(a*b);else st.push_back(a/b);}else st.push_back(stoi(t));}return st[0];} };",
    },
    "Validate Binary Search Tree": {
        "java": "class Solution { public boolean isValidBST(TreeNode root){return ok(root,Long.MIN_VALUE,Long.MAX_VALUE);} boolean ok(TreeNode n,long lo,long hi){if(n==null)return true;if(n.val<=lo||n.val>=hi)return false;return ok(n.left,lo,n.val)&&ok(n.right,n.val,hi);} }",
        "js": "var isValidBST=function(root){function ok(n,lo,hi){if(!n)return true;if(n.val<=lo||n.val>=hi)return false;return ok(n.left,lo,n.val)&&ok(n.right,n.val,hi);}return ok(root,-Infinity,Infinity);};",
        "cpp": "class Solution { public: bool ok(TreeNode* n,long long lo,long long hi){if(!n)return true;if(n->val<=lo||n->val>=hi)return false;return ok(n->left,lo,n->val)&&ok(n->right,n->val,hi);} bool isValidBST(TreeNode* root){return ok(root,LLONG_MIN,LLONG_MAX);} };",
    },
    "Kth Smallest Element in a BST": {
        "java": "class Solution { public int kthSmallest(TreeNode root,int k){Deque<TreeNode> st=new ArrayDeque<>();TreeNode cur=root;while(cur!=null||!st.isEmpty()){while(cur!=null){st.push(cur);cur=cur.left;}cur=st.pop();if(--k==0)return cur.val;cur=cur.right;}return -1;} }",
        "js": "var kthSmallest=function(root,k){const st=[];let cur=root;while(cur||st.length){while(cur){st.push(cur);cur=cur.left;}cur=st.pop();if(--k===0)return cur.val;cur=cur.right;}return -1;};",
        "cpp": "class Solution { public: int kthSmallest(TreeNode* root,int k){stack<TreeNode*> st;TreeNode* cur=root;while(cur||!st.empty()){while(cur){st.push(cur);cur=cur->left;}cur=st.top();st.pop();if(--k==0)return cur->val;cur=cur->right;}return -1;} };",
    },
    "Binary Tree Level Order Traversal": {
        "java": "class Solution { public List<List<Integer>> levelOrder(TreeNode root){List<List<Integer>> res=new ArrayList<>();if(root==null)return res;Deque<TreeNode> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){int sz=q.size();List<Integer> level=new ArrayList<>();for(int i=0;i<sz;i++){TreeNode n=q.poll();level.add(n.val);if(n.left!=null)q.add(n.left);if(n.right!=null)q.add(n.right);}res.add(level);}return res;} }",
        "js": "var levelOrder=function(root){const res=[];if(!root)return res;let q=[root];while(q.length){const level=[],nx=[];for(const n of q){level.push(n.val);if(n.left)nx.push(n.left);if(n.right)nx.push(n.right);}res.push(level);q=nx;}return res;};",
        "cpp": "class Solution { public: vector<vector<int>> levelOrder(TreeNode* root){vector<vector<int>> res;if(!root)return res;queue<TreeNode*> q;q.push(root);while(!q.empty()){int sz=q.size();vector<int> level;for(int i=0;i<sz;i++){TreeNode* n=q.front();q.pop();level.push_back(n->val);if(n->left)q.push(n->left);if(n->right)q.push(n->right);}res.push_back(level);}return res;} };",
    },
    "Binary Tree Right Side View": {
        "java": "class Solution { public List<Integer> rightSideView(TreeNode root){List<Integer> res=new ArrayList<>();if(root==null)return res;Deque<TreeNode> q=new ArrayDeque<>();q.add(root);while(!q.isEmpty()){int sz=q.size();TreeNode n=null;for(int i=0;i<sz;i++){n=q.poll();if(n.left!=null)q.add(n.left);if(n.right!=null)q.add(n.right);}res.add(n.val);}return res;} }",
        "js": "var rightSideView=function(root){const res=[];if(!root)return res;let q=[root];while(q.length){const nx=[];let last=null;for(const n of q){last=n;if(n.left)nx.push(n.left);if(n.right)nx.push(n.right);}res.push(last.val);q=nx;}return res;};",
        "cpp": "class Solution { public: vector<int> rightSideView(TreeNode* root){vector<int> res;if(!root)return res;queue<TreeNode*> q;q.push(root);while(!q.empty()){int sz=q.size();TreeNode* n=nullptr;for(int i=0;i<sz;i++){n=q.front();q.pop();if(n->left)q.push(n->left);if(n->right)q.push(n->right);}res.push_back(n->val);}return res;} };",
    },
    "Count Good Nodes in Binary Tree": {
        "java": "class Solution { public int goodNodes(TreeNode root){return dfs(root,Integer.MIN_VALUE);} int dfs(TreeNode n,int mx){if(n==null)return 0;int good=n.val>=mx?1:0;int nm=Math.max(mx,n.val);return good+dfs(n.left,nm)+dfs(n.right,nm);} }",
        "js": "var goodNodes=function(root){function dfs(n,mx){if(!n)return 0;const good=n.val>=mx?1:0,nm=Math.max(mx,n.val);return good+dfs(n.left,nm)+dfs(n.right,nm);}return dfs(root,-Infinity);};",
        "cpp": "class Solution { public: int dfs(TreeNode* n,int mx){if(!n)return 0;int good=n->val>=mx?1:0;int nm=max(mx,n->val);return good+dfs(n->left,nm)+dfs(n->right,nm);} int goodNodes(TreeNode* root){return dfs(root,INT_MIN);} };",
    },
    "Lowest Common Ancestor of a Binary Search Tree": {
        "java": "class Solution { public TreeNode lowestCommonAncestor(TreeNode root,int p,int q){TreeNode cur=root;while(cur!=null){if(p<cur.val&&q<cur.val)cur=cur.left;else if(p>cur.val&&q>cur.val)cur=cur.right;else return cur;}return null;} }",
        "js": "var lowestCommonAncestor=function(root,p,q){let cur=root;while(cur){if(p<cur.val&&q<cur.val)cur=cur.left;else if(p>cur.val&&q>cur.val)cur=cur.right;else return cur;}return null;};",
        "cpp": "class Solution { public: TreeNode* lowestCommonAncestor(TreeNode* root,int p,int q){TreeNode* cur=root;while(cur){if(p<cur->val&&q<cur->val)cur=cur->left;else if(p>cur->val&&q>cur->val)cur=cur->right;else return cur;}return nullptr;} };",
    },
    "Construct Binary Tree from Preorder and Inorder Traversal": {
        "java": "class Solution { int pre=0; Map<Integer,Integer> idx=new HashMap<>(); int[] preo; public TreeNode buildTree(int[] preorder,int[] inorder){preo=preorder;for(int i=0;i<inorder.length;i++)idx.put(inorder[i],i);return build(0,inorder.length-1);} TreeNode build(int lo,int hi){if(lo>hi)return null;int v=preo[pre++];TreeNode n=new TreeNode(v);int m=idx.get(v);n.left=build(lo,m-1);n.right=build(m+1,hi);return n;} }",
        "js": "var buildTree=function(preorder,inorder){const idx=new Map();inorder.forEach((v,i)=>idx.set(v,i));let pre=0;function build(lo,hi){if(lo>hi)return null;const v=preorder[pre++];const n=new TreeNode(v);const m=idx.get(v);n.left=build(lo,m-1);n.right=build(m+1,hi);return n;}return build(0,inorder.length-1);};",
        "cpp": "class Solution { public: int pre=0; unordered_map<int,int> idx; vector<int> preo; TreeNode* build(int lo,int hi){if(lo>hi)return nullptr;int v=preo[pre++];TreeNode* n=new TreeNode(v);int m=idx[v];n->left=build(lo,m-1);n->right=build(m+1,hi);return n;} TreeNode* buildTree(vector<int>& preorder,vector<int>& inorder){preo=preorder;for(int i=0;i<(int)inorder.size();i++)idx[inorder[i]]=i;return build(0,inorder.size()-1);} };",
    },
    "Subtree of Another Tree": {
        "java": "class Solution { public boolean isSubtree(TreeNode root,TreeNode sub){if(root==null)return false;return same(root,sub)||isSubtree(root.left,sub)||isSubtree(root.right,sub);} boolean same(TreeNode a,TreeNode b){if(a==null&&b==null)return true;if(a==null||b==null||a.val!=b.val)return false;return same(a.left,b.left)&&same(a.right,b.right);} }",
        "js": "var isSubtree=function(root,sub){function same(a,b){if(!a&&!b)return true;if(!a||!b||a.val!==b.val)return false;return same(a.left,b.left)&&same(a.right,b.right);}function dfs(n){if(!n)return false;return same(n,sub)||dfs(n.left)||dfs(n.right);}return dfs(root);};",
        "cpp": "class Solution { public: bool same(TreeNode* a,TreeNode* b){if(!a&&!b)return true;if(!a||!b||a->val!=b->val)return false;return same(a->left,b->left)&&same(a->right,b->right);} bool isSubtree(TreeNode* root,TreeNode* sub){if(!root)return false;return same(root,sub)||isSubtree(root->left,sub)||isSubtree(root->right,sub);} };",
    },
    "Add Two Numbers": {
        "java": "class Solution { public ListNode addTwoNumbers(ListNode l1,ListNode l2){ListNode d=new ListNode(0),cur=d;int carry=0;while(l1!=null||l2!=null||carry!=0){int s=carry;if(l1!=null){s+=l1.val;l1=l1.next;}if(l2!=null){s+=l2.val;l2=l2.next;}carry=s/10;cur.next=new ListNode(s%10);cur=cur.next;}return d.next;} }",
        "js": "var addTwoNumbers=function(l1,l2){const d=new ListNode(0);let cur=d,carry=0;while(l1||l2||carry){let s=carry;if(l1){s+=l1.val;l1=l1.next;}if(l2){s+=l2.val;l2=l2.next;}carry=Math.floor(s/10);cur.next=new ListNode(s%10);cur=cur.next;}return d.next;};",
        "cpp": "class Solution { public: ListNode* addTwoNumbers(ListNode* l1,ListNode* l2){ListNode d(0);ListNode* cur=&d;int carry=0;while(l1||l2||carry){int s=carry;if(l1){s+=l1->val;l1=l1->next;}if(l2){s+=l2->val;l2=l2->next;}carry=s/10;cur->next=new ListNode(s%10);cur=cur->next;}return d.next;} };",
    },
    "Remove Nth Node From End of List": {
        "java": "class Solution { public ListNode removeNthFromEnd(ListNode head,int n){ListNode d=new ListNode(0);d.next=head;ListNode f=d,s=d;for(int i=0;i<n;i++)f=f.next;while(f.next!=null){f=f.next;s=s.next;}s.next=s.next.next;return d.next;} }",
        "js": "var removeNthFromEnd=function(head,n){const d=new ListNode(0);d.next=head;let f=d,s=d;for(let i=0;i<n;i++)f=f.next;while(f.next){f=f.next;s=s.next;}s.next=s.next.next;return d.next;};",
        "cpp": "class Solution { public: ListNode* removeNthFromEnd(ListNode* head,int n){ListNode d(0);d.next=head;ListNode* f=&d;ListNode* s=&d;for(int i=0;i<n;i++)f=f->next;while(f->next){f=f->next;s=s->next;}s->next=s->next->next;return d.next;} };",
    },
    "Kth Largest Element in an Array": {
        "java": "class Solution { public int findKthLargest(int[] nums,int k){PriorityQueue<Integer> pq=new PriorityQueue<>();for(int v:nums){pq.add(v);if(pq.size()>k)pq.poll();}return pq.peek();} }",
        "js": "var findKthLargest=function(nums,k){nums.sort((a,b)=>b-a);return nums[k-1];};",
        "cpp": "class Solution { public: int findKthLargest(vector<int>& nums,int k){priority_queue<int,vector<int>,greater<int>> pq;for(int v:nums){pq.push(v);if((int)pq.size()>k)pq.pop();}return pq.top();} };",
    },
})

# ════════════════════════ HARD ════════════════════════
SOLUTIONS.update({

    "Trapping Rain Water": {
        "java": "class Solution { public int trap(int[] h){int l=0,r=h.length-1,lm=0,rm=0,res=0;while(l<r){if(h[l]<h[r]){lm=Math.max(lm,h[l]);res+=lm-h[l];l++;}else{rm=Math.max(rm,h[r]);res+=rm-h[r];r--;}}return res;} }",
        "js": "var trap=function(h){let l=0,r=h.length-1,lm=0,rm=0,res=0;while(l<r){if(h[l]<h[r]){lm=Math.max(lm,h[l]);res+=lm-h[l];l++;}else{rm=Math.max(rm,h[r]);res+=rm-h[r];r--;}}return res;};",
        "cpp": "class Solution { public: int trap(vector<int>& h){int l=0,r=h.size()-1,lm=0,rm=0,res=0;while(l<r){if(h[l]<h[r]){lm=max(lm,h[l]);res+=lm-h[l];l++;}else{rm=max(rm,h[r]);res+=rm-h[r];r--;}}return res;} };",
    },
    "Largest Rectangle in Histogram": {
        "java": "class Solution { public int largestRectangleArea(int[] heights){Deque<int[]> st=new ArrayDeque<>();int best=0,n=heights.length;for(int i=0;i<=n;i++){int h=(i==n)?0:heights[i];int start=i;while(!st.isEmpty()&&st.peek()[1]>h){int[] top=st.pop();best=Math.max(best,top[1]*(i-top[0]));start=top[0];}st.push(new int[]{start,h});}return best;} }",
        "js": "var largestRectangleArea=function(heights){const st=[];let best=0,n=heights.length;for(let i=0;i<=n;i++){const h=(i===n)?0:heights[i];let start=i;while(st.length&&st[st.length-1][1]>h){const top=st.pop();best=Math.max(best,top[1]*(i-top[0]));start=top[0];}st.push([start,h]);}return best;};",
        "cpp": "class Solution { public: int largestRectangleArea(vector<int>& heights){vector<pair<int,int>> st;int best=0,n=heights.size();for(int i=0;i<=n;i++){int h=(i==n)?0:heights[i];int start=i;while(!st.empty()&&st.back().second>h){auto top=st.back();st.pop_back();best=max(best,top.second*(i-top.first));start=top.first;}st.push_back({start,h});}return best;} };",
    },
    "Maximal Rectangle": {
        "java": "class Solution { public int maximalRectangle(char[][] m){if(m.length==0)return 0;int n=m[0].length;int[] h=new int[n];int best=0;for(char[] row:m){for(int j=0;j<n;j++)h[j]=row[j]=='1'?h[j]+1:0;best=Math.max(best,lr(h));}return best;} int lr(int[] heights){Deque<int[]> st=new ArrayDeque<>();int best=0,n=heights.length;for(int i=0;i<=n;i++){int h=(i==n)?0:heights[i];int start=i;while(!st.isEmpty()&&st.peek()[1]>h){int[] t=st.pop();best=Math.max(best,t[1]*(i-t[0]));start=t[0];}st.push(new int[]{start,h});}return best;} }",
        "js": "var maximalRectangle=function(m){if(m.length===0)return 0;const n=m[0].length,h=new Array(n).fill(0);let best=0;function lr(){const st=[];let b=0;for(let i=0;i<=n;i++){const hh=(i===n)?0:h[i];let start=i;while(st.length&&st[st.length-1][1]>hh){const t=st.pop();b=Math.max(b,t[1]*(i-t[0]));start=t[0];}st.push([start,hh]);}return b;}for(const row of m){for(let j=0;j<n;j++)h[j]=row[j]==='1'?h[j]+1:0;best=Math.max(best,lr());}return best;};",
        "cpp": "class Solution { public: int lr(vector<int>& heights){vector<pair<int,int>> st;int best=0,n=heights.size();for(int i=0;i<=n;i++){int h=(i==n)?0:heights[i];int start=i;while(!st.empty()&&st.back().second>h){auto t=st.back();st.pop_back();best=max(best,t.second*(i-t.first));start=t.first;}st.push_back({start,h});}return best;} int maximalRectangle(vector<vector<char>>& m){if(m.empty())return 0;int n=m[0].size();vector<int> h(n,0);int best=0;for(auto& row:m){for(int j=0;j<n;j++)h[j]=row[j]=='1'?h[j]+1:0;best=max(best,lr(h));}return best;} };",
    },
    "Sliding Window Maximum": {
        "java": "class Solution { public int[] maxSlidingWindow(int[] nums,int k){Deque<Integer> dq=new ArrayDeque<>();int n=nums.length;int[] res=new int[n-k+1];for(int i=0;i<n;i++){while(!dq.isEmpty()&&nums[dq.peekLast()]<=nums[i])dq.pollLast();dq.addLast(i);if(dq.peekFirst()<=i-k)dq.pollFirst();if(i>=k-1)res[i-k+1]=nums[dq.peekFirst()];}return res;} }",
        "js": "var maxSlidingWindow=function(nums,k){const dq=[],res=[];for(let i=0;i<nums.length;i++){while(dq.length&&nums[dq[dq.length-1]]<=nums[i])dq.pop();dq.push(i);if(dq[0]<=i-k)dq.shift();if(i>=k-1)res.push(nums[dq[0]]);}return res;};",
        "cpp": "class Solution { public: vector<int> maxSlidingWindow(vector<int>& nums,int k){deque<int> dq;vector<int> res;for(int i=0;i<(int)nums.size();i++){while(!dq.empty()&&nums[dq.back()]<=nums[i])dq.pop_back();dq.push_back(i);if(dq.front()<=i-k)dq.pop_front();if(i>=k-1)res.push_back(nums[dq.front()]);}return res;} };",
    },
    "First Missing Positive": {
        "java": "class Solution { public int firstMissingPositive(int[] nums){Set<Integer> s=new HashSet<>();for(int v:nums)s.add(v);int i=1;while(s.contains(i))i++;return i;} }",
        "js": "var firstMissingPositive=function(nums){const s=new Set(nums);let i=1;while(s.has(i))i++;return i;};",
        "cpp": "class Solution { public: int firstMissingPositive(vector<int>& nums){set<int> s(nums.begin(),nums.end());int i=1;while(s.count(i))i++;return i;} };",
    },
    "Candy": {
        "java": "class Solution { public int candy(int[] r){int n=r.length;int[] c=new int[n];Arrays.fill(c,1);for(int i=1;i<n;i++)if(r[i]>r[i-1])c[i]=c[i-1]+1;for(int i=n-2;i>=0;i--)if(r[i]>r[i+1])c[i]=Math.max(c[i],c[i+1]+1);int s=0;for(int v:c)s+=v;return s;} }",
        "js": "var candy=function(r){const n=r.length,c=new Array(n).fill(1);for(let i=1;i<n;i++)if(r[i]>r[i-1])c[i]=c[i-1]+1;for(let i=n-2;i>=0;i--)if(r[i]>r[i+1])c[i]=Math.max(c[i],c[i+1]+1);return c.reduce((a,b)=>a+b,0);};",
        "cpp": "class Solution { public: int candy(vector<int>& r){int n=r.size();vector<int> c(n,1);for(int i=1;i<n;i++)if(r[i]>r[i-1])c[i]=c[i-1]+1;for(int i=n-2;i>=0;i--)if(r[i]>r[i+1])c[i]=max(c[i],c[i+1]+1);int s=0;for(int v:c)s+=v;return s;} };",
    },
    "Jump Game II": {
        "java": "class Solution { public int jump(int[] nums){int jumps=0,end=0,far=0;for(int i=0;i<nums.length-1;i++){far=Math.max(far,i+nums[i]);if(i==end){jumps++;end=far;}}return jumps;} }",
        "js": "var jump=function(nums){let jumps=0,end=0,far=0;for(let i=0;i<nums.length-1;i++){far=Math.max(far,i+nums[i]);if(i===end){jumps++;end=far;}}return jumps;};",
        "cpp": "class Solution { public: int jump(vector<int>& nums){int jumps=0,end=0,far=0;for(int i=0;i<(int)nums.size()-1;i++){far=max(far,i+nums[i]);if(i==end){jumps++;end=far;}}return jumps;} };",
    },
    "Edit Distance": {
        "java": "class Solution { public int minDistance(String w1,String w2){int m=w1.length(),n=w2.length();int[] dp=new int[n+1];for(int j=0;j<=n;j++)dp[j]=j;for(int i=1;i<=m;i++){int prev=dp[0];dp[0]=i;for(int j=1;j<=n;j++){int cur=dp[j];if(w1.charAt(i-1)==w2.charAt(j-1))dp[j]=prev;else dp[j]=1+Math.min(prev,Math.min(dp[j],dp[j-1]));prev=cur;}}return dp[n];} }",
        "js": "var minDistance=function(w1,w2){const m=w1.length,n=w2.length;const dp=[];for(let j=0;j<=n;j++)dp[j]=j;for(let i=1;i<=m;i++){let prev=dp[0];dp[0]=i;for(let j=1;j<=n;j++){const cur=dp[j];if(w1[i-1]===w2[j-1])dp[j]=prev;else dp[j]=1+Math.min(prev,dp[j],dp[j-1]);prev=cur;}}return dp[n];};",
        "cpp": "class Solution { public: int minDistance(string w1,string w2){int m=w1.size(),n=w2.size();vector<int> dp(n+1);for(int j=0;j<=n;j++)dp[j]=j;for(int i=1;i<=m;i++){int prev=dp[0];dp[0]=i;for(int j=1;j<=n;j++){int cur=dp[j];if(w1[i-1]==w2[j-1])dp[j]=prev;else dp[j]=1+min(prev,min(dp[j],dp[j-1]));prev=cur;}}return dp[n];} };",
    },
    "Distinct Subsequences": {
        "java": "class Solution { public int numDistinct(String s,String t){int n=t.length();long[] dp=new long[n+1];dp[0]=1;for(char c:s.toCharArray())for(int j=n;j>=1;j--)if(c==t.charAt(j-1))dp[j]+=dp[j-1];return (int)dp[n];} }",
        "js": "var numDistinct=function(s,t){const n=t.length,dp=new Array(n+1).fill(0);dp[0]=1;for(const c of s)for(let j=n;j>=1;j--)if(c===t[j-1])dp[j]+=dp[j-1];return dp[n];};",
        "cpp": "class Solution { public: int numDistinct(string s,string t){int n=t.size();vector<long long> dp(n+1,0);dp[0]=1;for(char c:s)for(int j=n;j>=1;j--)if(c==t[j-1])dp[j]+=dp[j-1];return (int)dp[n];} };",
    },
    "Longest Valid Parentheses": {
        "java": "class Solution { public int longestValidParentheses(String s){Deque<Integer> st=new ArrayDeque<>();st.push(-1);int best=0;for(int i=0;i<s.length();i++){if(s.charAt(i)=='(')st.push(i);else{st.pop();if(st.isEmpty())st.push(i);else best=Math.max(best,i-st.peek());}}return best;} }",
        "js": "var longestValidParentheses=function(s){const st=[-1];let best=0;for(let i=0;i<s.length;i++){if(s[i]==='(')st.push(i);else{st.pop();if(st.length===0)st.push(i);else best=Math.max(best,i-st[st.length-1]);}}return best;};",
        "cpp": "class Solution { public: int longestValidParentheses(string s){vector<int> st;st.push_back(-1);int best=0;for(int i=0;i<(int)s.size();i++){if(s[i]=='(')st.push_back(i);else{st.pop_back();if(st.empty())st.push_back(i);else best=max(best,i-st.back());}}return best;} };",
    },
    "Best Time to Buy and Sell Stock III": {
        "java": "class Solution { public int maxProfit(int[] prices){int b1=Integer.MIN_VALUE,s1=0,b2=Integer.MIN_VALUE,s2=0;for(int p:prices){b1=Math.max(b1,-p);s1=Math.max(s1,b1+p);b2=Math.max(b2,s1-p);s2=Math.max(s2,b2+p);}return s2;} }",
        "js": "var maxProfit=function(prices){let b1=-Infinity,s1=0,b2=-Infinity,s2=0;for(const p of prices){b1=Math.max(b1,-p);s1=Math.max(s1,b1+p);b2=Math.max(b2,s1-p);s2=Math.max(s2,b2+p);}return s2;};",
        "cpp": "class Solution { public: int maxProfit(vector<int>& prices){long b1=LONG_MIN,s1=0,b2=LONG_MIN,s2=0;for(int p:prices){b1=max(b1,(long)-p);s1=max(s1,b1+p);b2=max(b2,s1-p);s2=max(s2,b2+p);}return (int)s2;} };",
    },
    "Best Time to Buy and Sell Stock IV": {
        "java": "class Solution { public int maxProfit(int k,int[] prices){if(prices.length==0||k==0)return 0;int[] buy=new int[k+1];int[] sell=new int[k+1];Arrays.fill(buy,Integer.MIN_VALUE/2);for(int p:prices)for(int t=1;t<=k;t++){buy[t]=Math.max(buy[t],sell[t-1]-p);sell[t]=Math.max(sell[t],buy[t]+p);}return sell[k];} }",
        "js": "var maxProfit=function(k,prices){if(prices.length===0||k===0)return 0;const buy=new Array(k+1).fill(-Infinity),sell=new Array(k+1).fill(0);for(const p of prices)for(let t=1;t<=k;t++){buy[t]=Math.max(buy[t],sell[t-1]-p);sell[t]=Math.max(sell[t],buy[t]+p);}return sell[k];};",
        "cpp": "class Solution { public: int maxProfit(int k,vector<int>& prices){if(prices.empty()||k==0)return 0;vector<long> buy(k+1,LONG_MIN/2),sell(k+1,0);for(int p:prices)for(int t=1;t<=k;t++){buy[t]=max(buy[t],sell[t-1]-p);sell[t]=max(sell[t],buy[t]+p);}return (int)sell[k];} };",
    },
    "Burst Balloons": {
        "java": "class Solution { public int maxCoins(int[] nums){int n=nums.length+2;int[] a=new int[n];a[0]=1;a[n-1]=1;for(int i=0;i<nums.length;i++)a[i+1]=nums[i];int[][] dp=new int[n][n];for(int len=2;len<n;len++)for(int l=0;l+len<n;l++){int r=l+len;for(int m=l+1;m<r;m++)dp[l][r]=Math.max(dp[l][r],dp[l][m]+a[l]*a[m]*a[r]+dp[m][r]);}return dp[0][n-1];} }",
        "js": "var maxCoins=function(nums){const a=[1,...nums,1],n=a.length;const dp=Array.from({length:n},()=>new Array(n).fill(0));for(let len=2;len<n;len++)for(let l=0;l+len<n;l++){const r=l+len;for(let m=l+1;m<r;m++)dp[l][r]=Math.max(dp[l][r],dp[l][m]+a[l]*a[m]*a[r]+dp[m][r]);}return dp[0][n-1];};",
        "cpp": "class Solution { public: int maxCoins(vector<int>& nums){int n=nums.size()+2;vector<int> a(n,1);for(int i=0;i<(int)nums.size();i++)a[i+1]=nums[i];vector<vector<int>> dp(n,vector<int>(n,0));for(int len=2;len<n;len++)for(int l=0;l+len<n;l++){int r=l+len;for(int m=l+1;m<r;m++)dp[l][r]=max(dp[l][r],dp[l][m]+a[l]*a[m]*a[r]+dp[m][r]);}return dp[0][n-1];} };",
    },
    "Regular Expression Matching": {
        "java": "class Solution { public boolean isMatch(String s,String p){int m=s.length(),n=p.length();boolean[][] dp=new boolean[m+1][n+1];dp[m][n]=true;for(int i=m;i>=0;i--)for(int j=n-1;j>=0;j--){boolean first=i<m&&(p.charAt(j)==s.charAt(i)||p.charAt(j)=='.');if(j+1<n&&p.charAt(j+1)=='*')dp[i][j]=dp[i][j+2]||(first&&dp[i+1][j]);else dp[i][j]=first&&dp[i+1][j+1];}return dp[0][0];} }",
        "js": "var isMatch=function(s,p){const m=s.length,n=p.length;const dp=Array.from({length:m+1},()=>new Array(n+1).fill(false));dp[m][n]=true;for(let i=m;i>=0;i--)for(let j=n-1;j>=0;j--){const first=i<m&&(p[j]===s[i]||p[j]==='.');if(j+1<n&&p[j+1]==='*')dp[i][j]=dp[i][j+2]||(first&&dp[i+1][j]);else dp[i][j]=first&&dp[i+1][j+1];}return dp[0][0];};",
        "cpp": "class Solution { public: bool isMatch(string s,string p){int m=s.size(),n=p.size();vector<vector<char>> dp(m+1,vector<char>(n+1,0));dp[m][n]=1;for(int i=m;i>=0;i--)for(int j=n-1;j>=0;j--){bool first=i<m&&(p[j]==s[i]||p[j]=='.');if(j+1<n&&p[j+1]=='*')dp[i][j]=dp[i][j+2]||(first&&dp[i+1][j]);else dp[i][j]=first&&dp[i+1][j+1];}return dp[0][0];} };",
    },
    "Wildcard Matching": {
        "java": "class Solution { public boolean isMatch(String s,String p){int m=s.length(),n=p.length();boolean[][] dp=new boolean[m+1][n+1];dp[m][n]=true;for(int j=n-1;j>=0;j--)dp[m][j]=p.charAt(j)=='*'&&dp[m][j+1];for(int i=m-1;i>=0;i--)for(int j=n-1;j>=0;j--){if(p.charAt(j)=='*')dp[i][j]=dp[i+1][j]||dp[i][j+1];else dp[i][j]=(p.charAt(j)=='?'||p.charAt(j)==s.charAt(i))&&dp[i+1][j+1];}return dp[0][0];} }",
        "js": "var isMatch=function(s,p){const m=s.length,n=p.length;const dp=Array.from({length:m+1},()=>new Array(n+1).fill(false));dp[m][n]=true;for(let j=n-1;j>=0;j--)dp[m][j]=p[j]==='*'&&dp[m][j+1];for(let i=m-1;i>=0;i--)for(let j=n-1;j>=0;j--){if(p[j]==='*')dp[i][j]=dp[i+1][j]||dp[i][j+1];else dp[i][j]=(p[j]==='?'||p[j]===s[i])&&dp[i+1][j+1];}return dp[0][0];};",
        "cpp": "class Solution { public: bool isMatch(string s,string p){int m=s.size(),n=p.size();vector<vector<char>> dp(m+1,vector<char>(n+1,0));dp[m][n]=1;for(int j=n-1;j>=0;j--)dp[m][j]=p[j]=='*'&&dp[m][j+1];for(int i=m-1;i>=0;i--)for(int j=n-1;j>=0;j--){if(p[j]=='*')dp[i][j]=dp[i+1][j]||dp[i][j+1];else dp[i][j]=(p[j]=='?'||p[j]==s[i])&&dp[i+1][j+1];}return dp[0][0];} };",
    },
    "Binary Tree Maximum Path Sum": {
        "java": "class Solution { int best=Integer.MIN_VALUE; public int maxPathSum(TreeNode root){gain(root);return best;} int gain(TreeNode n){if(n==null)return 0;int l=Math.max(gain(n.left),0);int r=Math.max(gain(n.right),0);best=Math.max(best,n.val+l+r);return n.val+Math.max(l,r);} }",
        "js": "var maxPathSum=function(root){let best=-Infinity;function gain(n){if(!n)return 0;const l=Math.max(gain(n.left),0),r=Math.max(gain(n.right),0);best=Math.max(best,n.val+l+r);return n.val+Math.max(l,r);}gain(root);return best;};",
        "cpp": "class Solution { public: int best=INT_MIN; int gain(TreeNode* n){if(!n)return 0;int l=max(gain(n->left),0);int r=max(gain(n->right),0);best=max(best,n->val+l+r);return n->val+max(l,r);} int maxPathSum(TreeNode* root){gain(root);return best;} };",
    },
    "Reverse Nodes in k-Group": {
        "java": "class Solution { public ListNode reverseKGroup(ListNode head,int k){ListNode node=head;int count=0;while(node!=null&&count<k){node=node.next;count++;}if(count<k)return head;ListNode prev=reverseKGroup(node,k);ListNode cur=head;for(int i=0;i<k;i++){ListNode nx=cur.next;cur.next=prev;prev=cur;cur=nx;}return prev;} }",
        "js": "var reverseKGroup=function(head,k){let node=head,count=0;while(node&&count<k){node=node.next;count++;}if(count<k)return head;let prev=reverseKGroup(node,k),cur=head;for(let i=0;i<k;i++){const nx=cur.next;cur.next=prev;prev=cur;cur=nx;}return prev;};",
        "cpp": "class Solution { public: ListNode* reverseKGroup(ListNode* head,int k){ListNode* node=head;int count=0;while(node&&count<k){node=node->next;count++;}if(count<k)return head;ListNode* prev=reverseKGroup(node,k);ListNode* cur=head;for(int i=0;i<k;i++){ListNode* nx=cur->next;cur->next=prev;prev=cur;cur=nx;}return prev;} };",
    },
    "Minimum Window Substring": {
        "java": "class Solution { public String minWindow(String s,String t){int[] need=new int[128];for(char c:t.toCharArray())need[c]++;int missing=t.length(),l=0,start=0,bestLen=Integer.MAX_VALUE;for(int r=0;r<s.length();r++){if(need[s.charAt(r)]>0)missing--;need[s.charAt(r)]--;while(missing==0){if(r-l+1<bestLen){bestLen=r-l+1;start=l;}need[s.charAt(l)]++;if(need[s.charAt(l)]>0)missing++;l++;}}return bestLen==Integer.MAX_VALUE?\"\":s.substring(start,start+bestLen);} }",
        "js": "var minWindow=function(s,t){const need=new Array(128).fill(0);for(const c of t)need[c.charCodeAt(0)]++;let missing=t.length,l=0,start=0,bestLen=Infinity;for(let r=0;r<s.length;r++){const cr=s.charCodeAt(r);if(need[cr]>0)missing--;need[cr]--;while(missing===0){if(r-l+1<bestLen){bestLen=r-l+1;start=l;}const cl=s.charCodeAt(l);need[cl]++;if(need[cl]>0)missing++;l++;}}return bestLen===Infinity?'':s.substring(start,start+bestLen);};",
        "cpp": "class Solution { public: string minWindow(string s,string t){int need[128]={0};for(char c:t)need[(int)c]++;int missing=t.size(),l=0,start=0,bestLen=INT_MAX;for(int r=0;r<(int)s.size();r++){if(need[(int)s[r]]>0)missing--;need[(int)s[r]]--;while(missing==0){if(r-l+1<bestLen){bestLen=r-l+1;start=l;}need[(int)s[l]]++;if(need[(int)s[l]]>0)missing++;l++;}}return bestLen==INT_MAX?\"\":s.substr(start,bestLen);} };",
    },
    "Word Ladder": {
        "java": "class Solution { public int ladderLength(String beginWord,String endWord,String[] wordList){Set<String> words=new HashSet<>(Arrays.asList(wordList));if(!words.contains(endWord))return 0;Deque<String> q=new ArrayDeque<>();q.add(beginWord);Map<String,Integer> dist=new HashMap<>();dist.put(beginWord,1);while(!q.isEmpty()){String w=q.poll();int d=dist.get(w);if(w.equals(endWord))return d;char[] arr=w.toCharArray();for(int i=0;i<arr.length;i++){char old=arr[i];for(char c='a';c<='z';c++){arr[i]=c;String nx=new String(arr);if(words.contains(nx)&&!dist.containsKey(nx)){dist.put(nx,d+1);q.add(nx);}}arr[i]=old;}}return 0;} }",
        "js": "var ladderLength=function(beginWord,endWord,wordList){const words=new Set(wordList);if(!words.has(endWord))return 0;const q=[[beginWord,1]];const seen=new Set([beginWord]);let head=0;while(head<q.length){const[w,d]=q[head++];if(w===endWord)return d;for(let i=0;i<w.length;i++)for(let c=97;c<=122;c++){const nx=w.slice(0,i)+String.fromCharCode(c)+w.slice(i+1);if(words.has(nx)&&!seen.has(nx)){seen.add(nx);q.push([nx,d+1]);}}}return 0;};",
        "cpp": "class Solution { public: int ladderLength(string beginWord,string endWord,vector<string>& wordList){set<string> words(wordList.begin(),wordList.end());if(!words.count(endWord))return 0;queue<pair<string,int>> q;q.push({beginWord,1});set<string> seen{beginWord};while(!q.empty()){auto pr=q.front();q.pop();string w=pr.first;int d=pr.second;if(w==endWord)return d;for(int i=0;i<(int)w.size();i++){char old=w[i];for(char c='a';c<='z';c++){w[i]=c;if(words.count(w)&&!seen.count(w)){seen.insert(w);q.push({w,d+1});}}w[i]=old;}}return 0;} };",
    },
    "Longest Increasing Path in a Matrix": {
        "java": "class Solution { int R,C;int[][] mat;int[][] memo; public int longestIncreasingPath(int[][] matrix){mat=matrix;R=matrix.length;C=matrix[0].length;memo=new int[R][C];int best=0;for(int i=0;i<R;i++)for(int j=0;j<C;j++)best=Math.max(best,dfs(i,j));return best;} int dfs(int r,int c){if(memo[r][c]!=0)return memo[r][c];int best=1;int[][] d={{1,0},{-1,0},{0,1},{0,-1}};for(int[] dd:d){int nr=r+dd[0],nc=c+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&mat[nr][nc]>mat[r][c])best=Math.max(best,1+dfs(nr,nc));}memo[r][c]=best;return best;} }",
        "js": "var longestIncreasingPath=function(matrix){const R=matrix.length,C=matrix[0].length;const memo=Array.from({length:R},()=>new Array(C).fill(0));function dfs(r,c){if(memo[r][c])return memo[r][c];let best=1;for(const[dr,dc] of [[1,0],[-1,0],[0,1],[0,-1]]){const nr=r+dr,nc=c+dc;if(nr>=0&&nr<R&&nc>=0&&nc<C&&matrix[nr][nc]>matrix[r][c])best=Math.max(best,1+dfs(nr,nc));}memo[r][c]=best;return best;}let best=0;for(let i=0;i<R;i++)for(let j=0;j<C;j++)best=Math.max(best,dfs(i,j));return best;};",
        "cpp": "class Solution { public: int R,C;vector<vector<int>>* mat;vector<vector<int>> memo; int dfs(int r,int c){if(memo[r][c])return memo[r][c];int best=1;int d[4][2]={{1,0},{-1,0},{0,1},{0,-1}};for(auto& dd:d){int nr=r+dd[0],nc=c+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&(*mat)[nr][nc]>(*mat)[r][c])best=max(best,1+dfs(nr,nc));}memo[r][c]=best;return best;} int longestIncreasingPath(vector<vector<int>>& matrix){mat=&matrix;R=matrix.size();C=matrix[0].size();memo.assign(R,vector<int>(C,0));int best=0;for(int i=0;i<R;i++)for(int j=0;j<C;j++)best=max(best,dfs(i,j));return best;} };",
    },
    "Pacific Atlantic Water Flow": {
        "java": "class Solution { public List<List<Integer>> pacificAtlantic(int[][] h){int R=h.length,C=h[0].length;boolean[][] pac=new boolean[R][C];boolean[][] atl=new boolean[R][C];for(int i=0;i<R;i++){bfs(h,pac,i,0);bfs(h,atl,i,C-1);}for(int j=0;j<C;j++){bfs(h,pac,0,j);bfs(h,atl,R-1,j);}List<List<Integer>> res=new ArrayList<>();for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(pac[i][j]&&atl[i][j])res.add(Arrays.asList(i,j));return res;} void bfs(int[][] h,boolean[][] seen,int sr,int sc){int R=h.length,C=h[0].length;Deque<int[]> q=new ArrayDeque<>();if(seen[sr][sc])return;seen[sr][sc]=true;q.add(new int[]{sr,sc});int[][] d={{1,0},{-1,0},{0,1},{0,-1}};while(!q.isEmpty()){int[] cur=q.poll();for(int[] dd:d){int nr=cur[0]+dd[0],nc=cur[1]+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&!seen[nr][nc]&&h[nr][nc]>=h[cur[0]][cur[1]]){seen[nr][nc]=true;q.add(new int[]{nr,nc});}}}} }",
        "js": "var pacificAtlantic=function(h){const R=h.length,C=h[0].length;const pac=Array.from({length:R},()=>new Array(C).fill(false));const atl=Array.from({length:R},()=>new Array(C).fill(false));function bfs(seen,sr,sc){if(seen[sr][sc])return;seen[sr][sc]=true;const q=[[sr,sc]];let head=0;while(head<q.length){const[r,c]=q[head++];for(const[dr,dc] of [[1,0],[-1,0],[0,1],[0,-1]]){const nr=r+dr,nc=c+dc;if(nr>=0&&nr<R&&nc>=0&&nc<C&&!seen[nr][nc]&&h[nr][nc]>=h[r][c]){seen[nr][nc]=true;q.push([nr,nc]);}}}}for(let i=0;i<R;i++){bfs(pac,i,0);bfs(atl,i,C-1);}for(let j=0;j<C;j++){bfs(pac,0,j);bfs(atl,R-1,j);}const res=[];for(let i=0;i<R;i++)for(let j=0;j<C;j++)if(pac[i][j]&&atl[i][j])res.push([i,j]);return res;};",
        "cpp": "class Solution { public: int R,C;vector<vector<int>>* H; void bfs(vector<vector<char>>& seen,int sr,int sc){if(seen[sr][sc])return;seen[sr][sc]=1;queue<pair<int,int>> q;q.push({sr,sc});int d[4][2]={{1,0},{-1,0},{0,1},{0,-1}};while(!q.empty()){auto pr=q.front();q.pop();int r=pr.first,c=pr.second;for(auto& dd:d){int nr=r+dd[0],nc=c+dd[1];if(nr>=0&&nr<R&&nc>=0&&nc<C&&!seen[nr][nc]&&(*H)[nr][nc]>=(*H)[r][c]){seen[nr][nc]=1;q.push({nr,nc});}}}} vector<vector<int>> pacificAtlantic(vector<vector<int>>& h){H=&h;R=h.size();C=h[0].size();vector<vector<char>> pac(R,vector<char>(C,0)),atl(R,vector<char>(C,0));for(int i=0;i<R;i++){bfs(pac,i,0);bfs(atl,i,C-1);}for(int j=0;j<C;j++){bfs(pac,0,j);bfs(atl,R-1,j);}vector<vector<int>> res;for(int i=0;i<R;i++)for(int j=0;j<C;j++)if(pac[i][j]&&atl[i][j])res.push_back({i,j});return res;} };",
    },
    "Min Cost to Connect All Points": {
        "java": "class Solution { public int minCostConnectPoints(int[][] pts){int n=pts.length;if(n<=1)return 0;boolean[] vis=new boolean[n];int[] dist=new int[n];Arrays.fill(dist,Integer.MAX_VALUE);dist[0]=0;int total=0;for(int it=0;it<n;it++){int u=-1;for(int i=0;i<n;i++)if(!vis[i]&&(u==-1||dist[i]<dist[u]))u=i;vis[u]=true;total+=dist[u];for(int v=0;v<n;v++)if(!vis[v]){int d=Math.abs(pts[u][0]-pts[v][0])+Math.abs(pts[u][1]-pts[v][1]);if(d<dist[v])dist[v]=d;}}return total;} }",
        "js": "var minCostConnectPoints=function(pts){const n=pts.length;if(n<=1)return 0;const vis=new Array(n).fill(false),dist=new Array(n).fill(Infinity);dist[0]=0;let total=0;for(let it=0;it<n;it++){let u=-1;for(let i=0;i<n;i++)if(!vis[i]&&(u===-1||dist[i]<dist[u]))u=i;vis[u]=true;total+=dist[u];for(let v=0;v<n;v++)if(!vis[v]){const d=Math.abs(pts[u][0]-pts[v][0])+Math.abs(pts[u][1]-pts[v][1]);if(d<dist[v])dist[v]=d;}}return total;};",
        "cpp": "class Solution { public: int minCostConnectPoints(vector<vector<int>>& pts){int n=pts.size();if(n<=1)return 0;vector<char> vis(n,0);vector<int> dist(n,INT_MAX);dist[0]=0;int total=0;for(int it=0;it<n;it++){int u=-1;for(int i=0;i<n;i++)if(!vis[i]&&(u==-1||dist[i]<dist[u]))u=i;vis[u]=1;total+=dist[u];for(int v=0;v<n;v++)if(!vis[v]){int d=abs(pts[u][0]-pts[v][0])+abs(pts[u][1]-pts[v][1]);if(d<dist[v])dist[v]=d;}}return total;} };",
    },
    "Network Delay Time": {
        "java": "class Solution { public int networkDelayTime(int[][] times,int n,int k){List<int[]>[] adj=new List[n+1];for(int i=1;i<=n;i++)adj[i]=new ArrayList<>();for(int[] t:times)adj[t[0]].add(new int[]{t[1],t[2]});int[] dist=new int[n+1];Arrays.fill(dist,Integer.MAX_VALUE);dist[k]=0;PriorityQueue<int[]> pq=new PriorityQueue<>((a,b)->Integer.compare(a[1],b[1]));pq.add(new int[]{k,0});while(!pq.isEmpty()){int[] cur=pq.poll();if(cur[1]>dist[cur[0]])continue;for(int[] e:adj[cur[0]]){int nd=cur[1]+e[1];if(nd<dist[e[0]]){dist[e[0]]=nd;pq.add(new int[]{e[0],nd});}}}int ans=0;for(int i=1;i<=n;i++){if(dist[i]==Integer.MAX_VALUE)return -1;ans=Math.max(ans,dist[i]);}return ans;} }",
        "js": "var networkDelayTime=function(times,n,k){const adj=Array.from({length:n+1},()=>[]);for(const[u,v,w] of times)adj[u].push([v,w]);const dist=new Array(n+1).fill(Infinity);dist[k]=0;const pq=[[0,k]];while(pq.length){pq.sort((a,b)=>a[0]-b[0]);const[d,u]=pq.shift();if(d>dist[u])continue;for(const[v,w] of adj[u]){const nd=d+w;if(nd<dist[v]){dist[v]=nd;pq.push([nd,v]);}}}let ans=0;for(let i=1;i<=n;i++){if(dist[i]===Infinity)return -1;ans=Math.max(ans,dist[i]);}return ans;};",
        "cpp": "class Solution { public: int networkDelayTime(vector<vector<int>>& times,int n,int k){vector<vector<pair<int,int>>> adj(n+1);for(auto& t:times)adj[t[0]].push_back({t[1],t[2]});vector<int> dist(n+1,INT_MAX);dist[k]=0;priority_queue<pair<int,int>,vector<pair<int,int>>,greater<>> pq;pq.push({0,k});while(!pq.empty()){auto pr=pq.top();pq.pop();int d=pr.first,u=pr.second;if(d>dist[u])continue;for(auto& e:adj[u]){int nd=d+e.second;if(nd<dist[e.first]){dist[e.first]=nd;pq.push({nd,e.first});}}}int ans=0;for(int i=1;i<=n;i++){if(dist[i]==INT_MAX)return -1;ans=max(ans,dist[i]);}return ans;} };",
    },
    "Cheapest Flights Within K Stops": {
        "java": "class Solution { public int findCheapestPrice(int n,int[][] flights,int src,int dst,int k){int INF=Integer.MAX_VALUE;int[] dist=new int[n];Arrays.fill(dist,INF);dist[src]=0;for(int i=0;i<=k;i++){int[] tmp=dist.clone();for(int[] f:flights){if(dist[f[0]]!=INF&&dist[f[0]]+f[2]<tmp[f[1]])tmp[f[1]]=dist[f[0]]+f[2];}dist=tmp;}return dist[dst]==INF?-1:dist[dst];} }",
        "js": "var findCheapestPrice=function(n,flights,src,dst,k){const INF=Infinity;let dist=new Array(n).fill(INF);dist[src]=0;for(let i=0;i<=k;i++){const tmp=dist.slice();for(const[u,v,w] of flights){if(dist[u]!==INF&&dist[u]+w<tmp[v])tmp[v]=dist[u]+w;}dist=tmp;}return dist[dst]===INF?-1:dist[dst];};",
        "cpp": "class Solution { public: int findCheapestPrice(int n,vector<vector<int>>& flights,int src,int dst,int k){const int INF=1e9;vector<int> dist(n,INF);dist[src]=0;for(int i=0;i<=k;i++){vector<int> tmp=dist;for(auto& f:flights){if(dist[f[0]]!=INF&&dist[f[0]]+f[2]<tmp[f[1]])tmp[f[1]]=dist[f[0]]+f[2];}dist=tmp;}return dist[dst]==INF?-1:dist[dst];} };",
    },
    "Russian Doll Envelopes": {
        "java": "class Solution { public int maxEnvelopes(int[][] env){Arrays.sort(env,(a,b)->a[0]!=b[0]?Integer.compare(a[0],b[0]):Integer.compare(b[1],a[1]));List<Integer> tails=new ArrayList<>();for(int[] e:env){int h=e[1];int lo=0,hi=tails.size();while(lo<hi){int m=(lo+hi)/2;if(tails.get(m)<h)lo=m+1;else hi=m;}if(lo==tails.size())tails.add(h);else tails.set(lo,h);}return tails.size();} }",
        "js": "var maxEnvelopes=function(env){env.sort((a,b)=>a[0]!==b[0]?a[0]-b[0]:b[1]-a[1]);const t=[];for(const[,h] of env){let lo=0,hi=t.length;while(lo<hi){const m=(lo+hi)>>1;if(t[m]<h)lo=m+1;else hi=m;}if(lo===t.length)t.push(h);else t[lo]=h;}return t.length;};",
        "cpp": "class Solution { public: int maxEnvelopes(vector<vector<int>>& env){sort(env.begin(),env.end(),[](const vector<int>&a,const vector<int>&b){return a[0]!=b[0]?a[0]<b[0]:a[1]>b[1];});vector<int> t;for(auto& e:env){int h=e[1];auto it=lower_bound(t.begin(),t.end(),h);if(it==t.end())t.push_back(h);else *it=h;}return t.size();} };",
    },
    "Count of Smaller Numbers After Self": {
        "java": "class Solution { public List<Integer> countSmaller(int[] nums){int n=nums.length;List<Integer> r=new ArrayList<>();for(int i=0;i<n;i++){int c=0;for(int j=i+1;j<n;j++)if(nums[j]<nums[i])c++;r.add(c);}return r;} }",
        "js": "var countSmaller=function(nums){const n=nums.length,r=[];for(let i=0;i<n;i++){let c=0;for(let j=i+1;j<n;j++)if(nums[j]<nums[i])c++;r.push(c);}return r;};",
        "cpp": "class Solution { public: vector<int> countSmaller(vector<int>& nums){int n=nums.size();vector<int> r;for(int i=0;i<n;i++){int c=0;for(int j=i+1;j<n;j++)if(nums[j]<nums[i])c++;r.push_back(c);}return r;} };",
    },
})
