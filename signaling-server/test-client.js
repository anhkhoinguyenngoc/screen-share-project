const { io } = require("socket.io-client");
const socket = io("http://localhost:3000", { transports: ["websocket"] });

socket.on("connect", () => {
  console.log("[CLIENT] connected");
  socket.emit("join-room", { room: "child-01" });
  console.log("[CLIENT] joined room child-01");
});

socket.on("offer", (data) => {
  console.log("[CLIENT] RECEIVED OFFER:", JSON.stringify(data).slice(0, 120) + "...");
  console.log("[CLIENT] → signaling pipeline OK");
});

socket.on("ice-candidate", (data) => {
  console.log("[CLIENT] received ice-candidate");
});
