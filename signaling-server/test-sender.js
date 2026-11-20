const { io } = require("socket.io-client");
const socket = io("http://localhost:3000", { transports: ["websocket"] });

socket.on("connect", () => {
  console.log("[SENDER] connected");
  socket.emit("join-room", { room: "child-01" });
  console.log("[SENDER] joined room child-01");

  setTimeout(() => {
    const fakeOffer = {
      room: "child-01",
      sdp: "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\na=sendonly\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n",
      type: "offer",
    };
    socket.emit("offer", fakeOffer);
    console.log("[SENDER] sent offer");
  }, 500);
});

socket.on("answer", (data) => console.log("[SENDER] got answer:", data));
