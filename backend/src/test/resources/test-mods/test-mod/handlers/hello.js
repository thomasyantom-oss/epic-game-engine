engine.on("test.hello", 100, function(event) {
    event.set("message", "hello from module");
});
