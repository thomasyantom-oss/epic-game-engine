① `git status --short`  
退出码：`1`  
前几行输出：
```text
fatal: detected dubious ownership in repository at 'C:/workplace/epic'
'C:/workplace/epic' is owned by:
        BUILTIN/Administrators (S-1-5-32-544)
but the current user is:
        THOM/thoma (S-1-5-21-2896712413-1920571259-3643241659-1002)
```

② `cd backend` 后 `mvn -q -version`  
退出码：`0`  
输出：
```text
3.9.16
```

③ `cd frontend` 后 `npm --version`  
退出码：`0`  
输出：
```text
11.13.0
```

未看到 execpolicy/.rules 拦截。Git 失败原因是 Git 的 dubious ownership / safe.directory 检查。