"""Correct solutions (python/java/js/cpp) for the 25 ORIGINAL coding questions
already in prod (AI-generated). Used by verify_orig25.py to submit each through
the live judge in all 4 languages and confirm a correct solution is accepted —
surfacing any original whose AI-authored testcases are wrong."""

SOL = {

"Best Time to Buy and Sell Stock": {
 "python":"class Solution:\n    def maxProfit(self, prices):\n        lo=float('inf');best=0\n        for p in prices:\n            lo=min(lo,p);best=max(best,p-lo)\n        return best\n",
 "java":"class Solution { public int maxProfit(int[] prices){int lo=Integer.MAX_VALUE,best=0;for(int p:prices){lo=Math.min(lo,p);best=Math.max(best,p-lo);}return best;} }",
 "javascript":"var maxProfit=function(prices){let lo=Infinity,best=0;for(const p of prices){lo=Math.min(lo,p);best=Math.max(best,p-lo);}return best;};",
 "cpp":"class Solution { public: int maxProfit(vector<int>& prices){int lo=INT_MAX,best=0;for(int p:prices){lo=min(lo,p);best=max(best,p-lo);}return best;} };",
},
"Binary Search": {
 "python":"class Solution:\n    def search(self, nums, target):\n        lo,hi=0,len(nums)-1\n        while lo<=hi:\n            m=(lo+hi)//2\n            if nums[m]==target:return m\n            if nums[m]<target:lo=m+1\n            else:hi=m-1\n        return -1\n",
 "java":"class Solution { public int search(int[] nums,int target){int lo=0,hi=nums.length-1;while(lo<=hi){int m=(lo+hi)/2;if(nums[m]==target)return m;if(nums[m]<target)lo=m+1;else hi=m-1;}return -1;} }",
 "javascript":"var search=function(nums,target){let lo=0,hi=nums.length-1;while(lo<=hi){const m=(lo+hi)>>1;if(nums[m]===target)return m;if(nums[m]<target)lo=m+1;else hi=m-1;}return -1;};",
 "cpp":"class Solution { public: int search(vector<int>& nums,int target){int lo=0,hi=nums.size()-1;while(lo<=hi){int m=(lo+hi)/2;if(nums[m]==target)return m;if(nums[m]<target)lo=m+1;else hi=m-1;}return -1;} };",
},
"Car Fleet": {
 "python":"class Solution:\n    def carFleet(self, target, position, speed):\n        cars=sorted(zip(position,speed),reverse=True)\n        fleets=0;cur=-1.0\n        for p,s in cars:\n            t=(target-p)/s\n            if t>cur:\n                fleets+=1;cur=t\n        return fleets\n",
 "java":"class Solution { public int carFleet(int target,int[] position,int[] speed){int n=position.length;Integer[] idx=new Integer[n];for(int i=0;i<n;i++)idx[i]=i;java.util.Arrays.sort(idx,(a,b)->position[b]-position[a]);int fleets=0;double cur=-1;for(int i:idx){double t=(double)(target-position[i])/speed[i];if(t>cur){fleets++;cur=t;}}return fleets;} }",
 "javascript":"var carFleet=function(target,position,speed){const n=position.length,idx=[...Array(n).keys()].sort((a,b)=>position[b]-position[a]);let fleets=0,cur=-1;for(const i of idx){const t=(target-position[i])/speed[i];if(t>cur){fleets++;cur=t;}}return fleets;};",
 "cpp":"class Solution { public: int carFleet(int target,vector<int>& position,vector<int>& speed){int n=position.size();vector<int> idx(n);for(int i=0;i<n;i++)idx[i]=i;sort(idx.begin(),idx.end(),[&](int a,int b){return position[a]>position[b];});int fleets=0;double cur=-1;for(int i:idx){double t=(double)(target-position[i])/speed[i];if(t>cur){fleets++;cur=t;}}return fleets;} };",
},
"Container With Most Water": {
 "python":"class Solution:\n    def maxArea(self, height):\n        l,r=0,len(height)-1;best=0\n        while l<r:\n            best=max(best,(r-l)*min(height[l],height[r]))\n            if height[l]<height[r]:l+=1\n            else:r-=1\n        return best\n",
 "java":"class Solution { public int maxArea(int[] h){int l=0,r=h.length-1,best=0;while(l<r){best=Math.max(best,(r-l)*Math.min(h[l],h[r]));if(h[l]<h[r])l++;else r--;}return best;} }",
 "javascript":"var maxArea=function(h){let l=0,r=h.length-1,best=0;while(l<r){best=Math.max(best,(r-l)*Math.min(h[l],h[r]));if(h[l]<h[r])l++;else r--;}return best;};",
 "cpp":"class Solution { public: int maxArea(vector<int>& h){int l=0,r=h.size()-1,best=0;while(l<r){best=max(best,(r-l)*min(h[l],h[r]));if(h[l]<h[r])l++;else r--;}return best;} };",
},
"Contains Duplicate": {
 "python":"class Solution:\n    def containsDuplicate(self, nums):\n        return len(set(nums))!=len(nums)\n",
 "java":"class Solution { public boolean containsDuplicate(int[] nums){java.util.Set<Integer> s=new java.util.HashSet<>();for(int v:nums)if(!s.add(v))return true;return false;} }",
 "javascript":"var containsDuplicate=function(nums){return new Set(nums).size!==nums.length;};",
 "cpp":"class Solution { public: bool containsDuplicate(vector<int>& nums){set<int> s(nums.begin(),nums.end());return s.size()!=nums.size();} };",
},
"Daily Temperatures": {
 "python":"class Solution:\n    def dailyTemperatures(self, temperatures):\n        n=len(temperatures);res=[0]*n;st=[]\n        for i,t in enumerate(temperatures):\n            while st and temperatures[st[-1]]<t:\n                j=st.pop();res[j]=i-j\n            st.append(i)\n        return res\n",
 "java":"class Solution { public int[] dailyTemperatures(int[] t){int n=t.length;int[] res=new int[n];java.util.Deque<Integer> st=new java.util.ArrayDeque<>();for(int i=0;i<n;i++){while(!st.isEmpty()&&t[st.peek()]<t[i]){int j=st.pop();res[j]=i-j;}st.push(i);}return res;} }",
 "javascript":"var dailyTemperatures=function(t){const n=t.length,res=new Array(n).fill(0),st=[];for(let i=0;i<n;i++){while(st.length&&t[st[st.length-1]]<t[i]){const j=st.pop();res[j]=i-j;}st.push(i);}return res;};",
 "cpp":"class Solution { public: vector<int> dailyTemperatures(vector<int>& t){int n=t.size();vector<int> res(n,0);vector<int> st;for(int i=0;i<n;i++){while(!st.empty()&&t[st.back()]<t[i]){int j=st.back();st.pop_back();res[j]=i-j;}st.push_back(i);}return res;} };",
},
"Delete the Middle Node of a Linked List": {
 "python":"class Solution:\n    def deleteMiddle(self, head):\n        if not head or not head.next:return None\n        slow=head;fast=head;prev=None\n        while fast and fast.next:\n            prev=slow;slow=slow.next;fast=fast.next.next\n        prev.next=slow.next\n        return head\n",
 "java":"class Solution { public ListNode deleteMiddle(ListNode head){if(head==null||head.next==null)return null;ListNode slow=head,fast=head,prev=null;while(fast!=null&&fast.next!=null){prev=slow;slow=slow.next;fast=fast.next.next;}prev.next=slow.next;return head;} }",
 "javascript":"var deleteMiddle=function(head){if(!head||!head.next)return null;let slow=head,fast=head,prev=null;while(fast&&fast.next){prev=slow;slow=slow.next;fast=fast.next.next;}prev.next=slow.next;return head;};",
 "cpp":"class Solution { public: ListNode* deleteMiddle(ListNode* head){if(!head||!head->next)return nullptr;ListNode* slow=head;ListNode* fast=head;ListNode* prev=nullptr;while(fast&&fast->next){prev=slow;slow=slow->next;fast=fast->next->next;}prev->next=slow->next;return head;} };",
},
"Jump Game": {
 "python":"class Solution:\n    def canJump(self, nums):\n        reach=0\n        for i,v in enumerate(nums):\n            if i>reach:return False\n            reach=max(reach,i+v)\n        return True\n",
 "java":"class Solution { public boolean canJump(int[] nums){int reach=0;for(int i=0;i<nums.length;i++){if(i>reach)return false;reach=Math.max(reach,i+nums[i]);}return true;} }",
 "javascript":"var canJump=function(nums){let reach=0;for(let i=0;i<nums.length;i++){if(i>reach)return false;reach=Math.max(reach,i+nums[i]);}return true;};",
 "cpp":"class Solution { public: bool canJump(vector<int>& nums){int reach=0;for(int i=0;i<(int)nums.size();i++){if(i>reach)return false;reach=max(reach,i+nums[i]);}return true;} };",
},
"Koko Eating Bananas": {
 "python":"class Solution:\n    def minEatingSpeed(self, piles, h):\n        import math\n        lo,hi=1,max(piles)\n        while lo<hi:\n            m=(lo+hi)//2\n            if sum(math.ceil(p/m) for p in piles)<=h:hi=m\n            else:lo=m+1\n        return lo\n",
 "java":"class Solution { public int minEatingSpeed(int[] piles,int h){int lo=1,hi=0;for(int p:piles)hi=Math.max(hi,p);while(lo<hi){int m=(lo+hi)/2;long need=0;for(int p:piles)need+=(p+m-1)/m;if(need<=h)hi=m;else lo=m+1;}return lo;} }",
 "javascript":"var minEatingSpeed=function(piles,h){let lo=1,hi=Math.max(...piles);while(lo<hi){const m=(lo+hi)>>1;let need=0;for(const p of piles)need+=Math.ceil(p/m);if(need<=h)hi=m;else lo=m+1;}return lo;};",
 "cpp":"class Solution { public: int minEatingSpeed(vector<int>& piles,int h){int lo=1,hi=0;for(int p:piles)hi=max(hi,p);while(lo<hi){int m=(lo+hi)/2;long long need=0;for(int p:piles)need+=(p+m-1)/m;if(need<=h)hi=m;else lo=m+1;}return lo;} };",
},
"Longest Consecutive Sequence": {
 "python":"class Solution:\n    def longestConsecutive(self, nums):\n        s=set(nums);best=0\n        for x in s:\n            if x-1 not in s:\n                y=x\n                while y+1 in s:y+=1\n                best=max(best,y-x+1)\n        return best\n",
 "java":"class Solution { public int longestConsecutive(int[] nums){java.util.Set<Integer> s=new java.util.HashSet<>();for(int v:nums)s.add(v);int best=0;for(int x:s){if(!s.contains(x-1)){int y=x;while(s.contains(y+1))y++;best=Math.max(best,y-x+1);}}return best;} }",
 "javascript":"var longestConsecutive=function(nums){const s=new Set(nums);let best=0;for(const x of s){if(!s.has(x-1)){let y=x;while(s.has(y+1))y++;best=Math.max(best,y-x+1);}}return best;};",
 "cpp":"class Solution { public: int longestConsecutive(vector<int>& nums){unordered_set<int> s(nums.begin(),nums.end());int best=0;for(int x:s){if(!s.count(x-1)){int y=x;while(s.count(y+1))y++;best=max(best,y-x+1);}}return best;} };",
},
"Longest Repeating Character Replacement": {
 "python":"class Solution:\n    def characterReplacement(self, s, k):\n        from collections import Counter\n        cnt=Counter();l=0;best=0;mx=0\n        for r in range(len(s)):\n            cnt[s[r]]+=1;mx=max(mx,cnt[s[r]])\n            while (r-l+1)-mx>k:\n                cnt[s[l]]-=1;l+=1\n            best=max(best,r-l+1)\n        return best\n",
 "java":"class Solution { public int characterReplacement(String s,int k){int[] cnt=new int[26];int l=0,best=0,mx=0;for(int r=0;r<s.length();r++){cnt[s.charAt(r)-'A']++;mx=Math.max(mx,cnt[s.charAt(r)-'A']);while((r-l+1)-mx>k){cnt[s.charAt(l)-'A']--;l++;}best=Math.max(best,r-l+1);}return best;} }",
 "javascript":"var characterReplacement=function(s,k){const cnt={};let l=0,best=0,mx=0;for(let r=0;r<s.length;r++){cnt[s[r]]=(cnt[s[r]]||0)+1;mx=Math.max(mx,cnt[s[r]]);while((r-l+1)-mx>k){cnt[s[l]]--;l++;}best=Math.max(best,r-l+1);}return best;};",
 "cpp":"class Solution { public: int characterReplacement(string s,int k){int cnt[26]={0};int l=0,best=0,mx=0;for(int r=0;r<(int)s.size();r++){cnt[s[r]-'A']++;mx=max(mx,cnt[s[r]-'A']);while((r-l+1)-mx>k){cnt[s[l]-'A']--;l++;}best=max(best,r-l+1);}return best;} };",
},
"Longest Substring Without Repeating Characters": {
 "python":"class Solution:\n    def lengthOfLongestSubstring(self, s):\n        seen={};l=0;best=0\n        for r,c in enumerate(s):\n            if c in seen and seen[c]>=l:l=seen[c]+1\n            seen[c]=r;best=max(best,r-l+1)\n        return best\n",
 "java":"class Solution { public int lengthOfLongestSubstring(String s){java.util.Map<Character,Integer> seen=new java.util.HashMap<>();int l=0,best=0;for(int r=0;r<s.length();r++){char c=s.charAt(r);if(seen.containsKey(c)&&seen.get(c)>=l)l=seen.get(c)+1;seen.put(c,r);best=Math.max(best,r-l+1);}return best;} }",
 "javascript":"var lengthOfLongestSubstring=function(s){const seen=new Map();let l=0,best=0;for(let r=0;r<s.length;r++){const c=s[r];if(seen.has(c)&&seen.get(c)>=l)l=seen.get(c)+1;seen.set(c,r);best=Math.max(best,r-l+1);}return best;};",
 "cpp":"class Solution { public: int lengthOfLongestSubstring(string s){unordered_map<char,int> seen;int l=0,best=0;for(int r=0;r<(int)s.size();r++){char c=s[r];if(seen.count(c)&&seen[c]>=l)l=seen[c]+1;seen[c]=r;best=max(best,r-l+1);}return best;} };",
},
"Maximum Subarray": {
 "python":"class Solution:\n    def maxSubArray(self, nums):\n        best=cur=nums[0]\n        for v in nums[1:]:\n            cur=max(v,cur+v);best=max(best,cur)\n        return best\n",
 "java":"class Solution { public int maxSubArray(int[] nums){int best=nums[0],cur=nums[0];for(int i=1;i<nums.length;i++){cur=Math.max(nums[i],cur+nums[i]);best=Math.max(best,cur);}return best;} }",
 "javascript":"var maxSubArray=function(nums){let best=nums[0],cur=nums[0];for(let i=1;i<nums.length;i++){cur=Math.max(nums[i],cur+nums[i]);best=Math.max(best,cur);}return best;};",
 "cpp":"class Solution { public: int maxSubArray(vector<int>& nums){int best=nums[0],cur=nums[0];for(size_t i=1;i<nums.size();i++){cur=max(nums[i],cur+nums[i]);best=max(best,cur);}return best;} };",
},
"Merge Sorted Array": {
 "python":"class Solution:\n    def merge(self, nums1, m, nums2, n):\n        i=m-1;j=n-1;k=m+n-1\n        while j>=0:\n            if i>=0 and nums1[i]>nums2[j]:\n                nums1[k]=nums1[i];i-=1\n            else:\n                nums1[k]=nums2[j];j-=1\n            k-=1\n",
 "java":"class Solution { public void merge(int[] nums1,int m,int[] nums2,int n){int i=m-1,j=n-1,k=m+n-1;while(j>=0){if(i>=0&&nums1[i]>nums2[j])nums1[k--]=nums1[i--];else nums1[k--]=nums2[j--];}} }",
 "javascript":"var merge=function(nums1,m,nums2,n){let i=m-1,j=n-1,k=m+n-1;while(j>=0){if(i>=0&&nums1[i]>nums2[j])nums1[k--]=nums1[i--];else nums1[k--]=nums2[j--];}};",
 "cpp":"class Solution { public: void merge(vector<int>& nums1,int m,vector<int>& nums2,int n){int i=m-1,j=n-1,k=m+n-1;while(j>=0){if(i>=0&&nums1[i]>nums2[j])nums1[k--]=nums1[i--];else nums1[k--]=nums2[j--];}} };",
},
"Permutation in String": {
 "python":"class Solution:\n    def checkInclusion(self, s1, s2):\n        from collections import Counter\n        if len(s1)>len(s2):return False\n        need=Counter(s1);win=Counter(s2[:len(s1)])\n        if win==need:return True\n        for i in range(len(s1),len(s2)):\n            win[s2[i]]+=1;win[s2[i-len(s1)]]-=1\n            if win[s2[i-len(s1)]]==0:del win[s2[i-len(s1)]]\n            if win==need:return True\n        return False\n",
 "java":"class Solution { public boolean checkInclusion(String s1,String s2){if(s1.length()>s2.length())return false;int[] need=new int[26],win=new int[26];for(char c:s1.toCharArray())need[c-'a']++;for(int i=0;i<s2.length();i++){win[s2.charAt(i)-'a']++;if(i>=s1.length())win[s2.charAt(i-s1.length())-'a']--;if(java.util.Arrays.equals(need,win))return true;}return false;} }",
 "javascript":"var checkInclusion=function(s1,s2){if(s1.length>s2.length)return false;const need=new Array(26).fill(0),win=new Array(26).fill(0);const A='a'.charCodeAt(0);for(const c of s1)need[c.charCodeAt(0)-A]++;for(let i=0;i<s2.length;i++){win[s2.charCodeAt(i)-A]++;if(i>=s1.length)win[s2.charCodeAt(i-s1.length)-A]--;if(need.every((v,k)=>v===win[k]))return true;}return false;};",
 "cpp":"class Solution { public: bool checkInclusion(string s1,string s2){if(s1.size()>s2.size())return false;int need[26]={0},win[26]={0};for(char c:s1)need[c-'a']++;for(int i=0;i<(int)s2.size();i++){win[s2[i]-'a']++;if(i>=(int)s1.size())win[s2[i-s1.size()]-'a']--;bool eq=true;for(int k=0;k<26;k++)if(need[k]!=win[k]){eq=false;break;}if(eq)return true;}return false;} };",
},
"Product of Array Except Self": {
 "python":"class Solution:\n    def productExceptSelf(self, nums):\n        n=len(nums);res=[1]*n\n        p=1\n        for i in range(n):res[i]=p;p*=nums[i]\n        p=1\n        for i in range(n-1,-1,-1):res[i]*=p;p*=nums[i]\n        return res\n",
 "java":"class Solution { public int[] productExceptSelf(int[] nums){int n=nums.length;int[] res=new int[n];int p=1;for(int i=0;i<n;i++){res[i]=p;p*=nums[i];}p=1;for(int i=n-1;i>=0;i--){res[i]*=p;p*=nums[i];}return res;} }",
 "javascript":"var productExceptSelf=function(nums){const n=nums.length,res=new Array(n);let p=1;for(let i=0;i<n;i++){res[i]=p;p*=nums[i];}p=1;for(let i=n-1;i>=0;i--){res[i]*=p;p*=nums[i];}return res;};",
 "cpp":"class Solution { public: vector<int> productExceptSelf(vector<int>& nums){int n=nums.size();vector<int> res(n,1);int p=1;for(int i=0;i<n;i++){res[i]=p;p*=nums[i];}p=1;for(int i=n-1;i>=0;i--){res[i]*=p;p*=nums[i];}return res;} };",
},
"Reverse Linked List II": {
 "python":"class Solution:\n    def reverseBetween(self, head, left, right):\n        dummy=ListNode(0);dummy.next=head;prev=dummy\n        for _ in range(left-1):prev=prev.next\n        cur=prev.next\n        for _ in range(right-left):\n            nx=cur.next;cur.next=nx.next;nx.next=prev.next;prev.next=nx\n        return dummy.next\n",
 "java":"class Solution { public ListNode reverseBetween(ListNode head,int left,int right){ListNode dummy=new ListNode(0);dummy.next=head;ListNode prev=dummy;for(int i=0;i<left-1;i++)prev=prev.next;ListNode cur=prev.next;for(int i=0;i<right-left;i++){ListNode nx=cur.next;cur.next=nx.next;nx.next=prev.next;prev.next=nx;}return dummy.next;} }",
 "javascript":"var reverseBetween=function(head,left,right){const dummy=new ListNode(0);dummy.next=head;let prev=dummy;for(let i=0;i<left-1;i++)prev=prev.next;let cur=prev.next;for(let i=0;i<right-left;i++){const nx=cur.next;cur.next=nx.next;nx.next=prev.next;prev.next=nx;}return dummy.next;};",
 "cpp":"class Solution { public: ListNode* reverseBetween(ListNode* head,int left,int right){ListNode dummy(0);dummy.next=head;ListNode* prev=&dummy;for(int i=0;i<left-1;i++)prev=prev->next;ListNode* cur=prev->next;for(int i=0;i<right-left;i++){ListNode* nx=cur->next;cur->next=nx->next;nx->next=prev->next;prev->next=nx;}return dummy.next;} };",
},
"Search in Rotated Sorted Array": {
 "python":"class Solution:\n    def search(self, nums, target):\n        lo,hi=0,len(nums)-1\n        while lo<=hi:\n            m=(lo+hi)//2\n            if nums[m]==target:return m\n            if nums[lo]<=nums[m]:\n                if nums[lo]<=target<nums[m]:hi=m-1\n                else:lo=m+1\n            else:\n                if nums[m]<target<=nums[hi]:lo=m+1\n                else:hi=m-1\n        return -1\n",
 "java":"class Solution { public int search(int[] nums,int target){int lo=0,hi=nums.length-1;while(lo<=hi){int m=(lo+hi)/2;if(nums[m]==target)return m;if(nums[lo]<=nums[m]){if(nums[lo]<=target&&target<nums[m])hi=m-1;else lo=m+1;}else{if(nums[m]<target&&target<=nums[hi])lo=m+1;else hi=m-1;}}return -1;} }",
 "javascript":"var search=function(nums,target){let lo=0,hi=nums.length-1;while(lo<=hi){const m=(lo+hi)>>1;if(nums[m]===target)return m;if(nums[lo]<=nums[m]){if(nums[lo]<=target&&target<nums[m])hi=m-1;else lo=m+1;}else{if(nums[m]<target&&target<=nums[hi])lo=m+1;else hi=m-1;}}return -1;};",
 "cpp":"class Solution { public: int search(vector<int>& nums,int target){int lo=0,hi=nums.size()-1;while(lo<=hi){int m=(lo+hi)/2;if(nums[m]==target)return m;if(nums[lo]<=nums[m]){if(nums[lo]<=target&&target<nums[m])hi=m-1;else lo=m+1;}else{if(nums[m]<target&&target<=nums[hi])lo=m+1;else hi=m-1;}}return -1;} };",
},
"Top K Frequent Elements": {
 "python":"class Solution:\n    def topKFrequent(self, nums, k):\n        from collections import Counter\n        return [v for v,_ in Counter(nums).most_common(k)]\n",
 "java":"class Solution { public int[] topKFrequent(int[] nums,int k){java.util.Map<Integer,Integer> cnt=new java.util.HashMap<>();for(int v:nums)cnt.merge(v,1,Integer::sum);java.util.List<Integer> keys=new java.util.ArrayList<>(cnt.keySet());keys.sort((a,b)->cnt.get(b)-cnt.get(a));int[] res=new int[k];for(int i=0;i<k;i++)res[i]=keys.get(i);return res;} }",
 "javascript":"var topKFrequent=function(nums,k){const cnt=new Map();for(const v of nums)cnt.set(v,(cnt.get(v)||0)+1);return [...cnt.keys()].sort((a,b)=>cnt.get(b)-cnt.get(a)).slice(0,k);};",
 "cpp":"class Solution { public: vector<int> topKFrequent(vector<int>& nums,int k){unordered_map<int,int> cnt;for(int v:nums)cnt[v]++;vector<pair<int,int>> v(cnt.begin(),cnt.end());sort(v.begin(),v.end(),[](auto&a,auto&b){return a.second>b.second;});vector<int> res;for(int i=0;i<k;i++)res.push_back(v[i].first);return res;} };",
},
"Two Sum": {
 "python":"class Solution:\n    def twoSum(self, nums, target):\n        seen={}\n        for i,v in enumerate(nums):\n            if target-v in seen:return [seen[target-v],i]\n            seen[v]=i\n        return []\n",
 "java":"class Solution { public int[] twoSum(int[] nums,int target){java.util.Map<Integer,Integer> seen=new java.util.HashMap<>();for(int i=0;i<nums.length;i++){if(seen.containsKey(target-nums[i]))return new int[]{seen.get(target-nums[i]),i};seen.put(nums[i],i);}return new int[]{};} }",
 "javascript":"var twoSum=function(nums,target){const seen=new Map();for(let i=0;i<nums.length;i++){if(seen.has(target-nums[i]))return [seen.get(target-nums[i]),i];seen.set(nums[i],i);}return [];};",
 "cpp":"class Solution { public: vector<int> twoSum(vector<int>& nums,int target){unordered_map<int,int> seen;for(int i=0;i<(int)nums.size();i++){if(seen.count(target-nums[i]))return {seen[target-nums[i]],i};seen[nums[i]]=i;}return {};} };",
},
"Two Sum II - Input Array Is Sorted": {
 "python":"class Solution:\n    def twoSum(self, numbers, target):\n        l,r=0,len(numbers)-1\n        while l<r:\n            s=numbers[l]+numbers[r]\n            if s==target:return [l+1,r+1]\n            if s<target:l+=1\n            else:r-=1\n        return []\n",
 "java":"class Solution { public int[] twoSum(int[] numbers,int target){int l=0,r=numbers.length-1;while(l<r){int s=numbers[l]+numbers[r];if(s==target)return new int[]{l+1,r+1};if(s<target)l++;else r--;}return new int[]{};} }",
 "javascript":"var twoSum=function(numbers,target){let l=0,r=numbers.length-1;while(l<r){const s=numbers[l]+numbers[r];if(s===target)return [l+1,r+1];if(s<target)l++;else r--;}return [];};",
 "cpp":"class Solution { public: vector<int> twoSum(vector<int>& numbers,int target){int l=0,r=numbers.size()-1;while(l<r){int s=numbers[l]+numbers[r];if(s==target)return {l+1,r+1};if(s<target)l++;else r--;}return {};} };",
},
"Valid Anagram": {
 "python":"class Solution:\n    def isAnagram(self, s, t):\n        from collections import Counter\n        return Counter(s)==Counter(t)\n",
 "java":"class Solution { public boolean isAnagram(String s,String t){if(s.length()!=t.length())return false;int[] c=new int[26];for(char ch:s.toCharArray())c[ch-'a']++;for(char ch:t.toCharArray())if(--c[ch-'a']<0)return false;return true;} }",
 "javascript":"var isAnagram=function(s,t){if(s.length!==t.length)return false;const c={};for(const ch of s)c[ch]=(c[ch]||0)+1;for(const ch of t){if(!c[ch])return false;c[ch]--;}return true;};",
 "cpp":"class Solution { public: bool isAnagram(string s,string t){if(s.size()!=t.size())return false;int c[26]={0};for(char ch:s)c[ch-'a']++;for(char ch:t)if(--c[ch-'a']<0)return false;return true;} };",
},
"Valid Palindrome": {
 "python":"class Solution:\n    def isPalindrome(self, s):\n        t=[c.lower() for c in s if c.isalnum()]\n        return t==t[::-1]\n",
 "java":"class Solution { public boolean isPalindrome(String s){int l=0,r=s.length()-1;while(l<r){while(l<r&&!Character.isLetterOrDigit(s.charAt(l)))l++;while(l<r&&!Character.isLetterOrDigit(s.charAt(r)))r--;if(Character.toLowerCase(s.charAt(l))!=Character.toLowerCase(s.charAt(r)))return false;l++;r--;}return true;} }",
 "javascript":"var isPalindrome=function(s){const t=s.toLowerCase().replace(/[^a-z0-9]/g,'');return t===t.split('').reverse().join('');};",
 "cpp":"class Solution { public: bool isPalindrome(string s){int l=0,r=s.size()-1;while(l<r){while(l<r&&!isalnum((unsigned char)s[l]))l++;while(l<r&&!isalnum((unsigned char)s[r]))r--;if(tolower((unsigned char)s[l])!=tolower((unsigned char)s[r]))return false;l++;r--;}return true;} };",
},
"Valid Parentheses": {
 "python":"class Solution:\n    def isValid(self, s):\n        st=[];m={')':'(',']':'[','}':'{'}\n        for c in s:\n            if c in m:\n                if not st or st.pop()!=m[c]:return False\n            else:st.append(c)\n        return not st\n",
 "java":"class Solution { public boolean isValid(String s){java.util.Deque<Character> st=new java.util.ArrayDeque<>();for(char c:s.toCharArray()){if(c=='(')st.push(')');else if(c=='[')st.push(']');else if(c=='{')st.push('}');else if(st.isEmpty()||st.pop()!=c)return false;}return st.isEmpty();} }",
 "javascript":"var isValid=function(s){const st=[],m={')':'(',']':'[','}':'{'};for(const c of s){if(m[c]){if(st.pop()!==m[c])return false;}else st.push(c);}return st.length===0;};",
 "cpp":"class Solution { public: bool isValid(string s){vector<char> st;for(char c:s){if(c=='(')st.push_back(')');else if(c=='[')st.push_back(']');else if(c=='{')st.push_back('}');else{if(st.empty()||st.back()!=c)return false;st.pop_back();}}return st.empty();} };",
},
"Valid Sudoku": {
 "python":"class Solution:\n    def isValidSudoku(self, board):\n        seen=set()\n        for i in range(9):\n            for j in range(9):\n                v=board[i][j]\n                if v!='.':\n                    for key in ((v,'r',i),(v,'c',j),(v,'b',i//3,j//3)):\n                        if key in seen:return False\n                        seen.add(key)\n        return True\n",
 "java":"class Solution { public boolean isValidSudoku(char[][] b){java.util.Set<String> seen=new java.util.HashSet<>();for(int i=0;i<9;i++)for(int j=0;j<9;j++){char v=b[i][j];if(v!='.'){if(!seen.add(\"r\"+i+v)||!seen.add(\"c\"+j+v)||!seen.add(\"b\"+(i/3)+(j/3)+v))return false;}}return true;} }",
 "javascript":"var isValidSudoku=function(b){const seen=new Set();for(let i=0;i<9;i++)for(let j=0;j<9;j++){const v=b[i][j];if(v!=='.'){const r='r'+i+v,c='c'+j+v,bx='b'+Math.floor(i/3)+Math.floor(j/3)+v;if(seen.has(r)||seen.has(c)||seen.has(bx))return false;seen.add(r);seen.add(c);seen.add(bx);}}return true;};",
 "cpp":"class Solution { public: bool isValidSudoku(vector<vector<char>>& b){set<string> seen;for(int i=0;i<9;i++)for(int j=0;j<9;j++){char v=b[i][j];if(v!='.'){string r=\"r\"+to_string(i)+v,c=\"c\"+to_string(j)+v,bx=\"b\"+to_string(i/3)+to_string(j/3)+v;if(seen.count(r)||seen.count(c)||seen.count(bx))return false;seen.insert(r);seen.insert(c);seen.insert(bx);}}return true;} };",
},

}
