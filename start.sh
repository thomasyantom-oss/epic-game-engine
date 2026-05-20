#!/bin/bash
cd "$(dirname "$0")"

JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-21.jdk/Contents/Home
MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"

echo "启动后端..."
cd backend
JAVA_HOME="$JAVA_HOME" "$MVN" spring-boot:run > /tmp/epic-backend.log 2>&1 &
BACKEND_PID=$!
cd ..

sleep 8
if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo "后端启动失败，查看 /tmp/epic-backend.log"
    exit 1
fi
echo "后端已启动 (PID: $BACKEND_PID)"

echo "启动前端..."
cd frontend
npm run dev > /tmp/epic-frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

sleep 3
echo "前端已启动 (PID: $FRONTEND_PID)"
echo ""
echo "==============================="
echo "  Epic Engine 已启动"
echo "  浏览器打开: http://localhost:5173"
echo "==============================="
echo ""
echo "按 Ctrl+C 停止所有服务"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; echo '已停止'; exit 0" INT TERM
wait
