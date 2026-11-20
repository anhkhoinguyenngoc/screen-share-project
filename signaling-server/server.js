const express = require("express");
const http = require("http");
const { Server } = require("socket.io");
const path = require("path");

const app = express();
const server = http.createServer(app);

const io = new Server(server, {
  cors: { origin: "*" },
  allowEIO3: true,
  pingInterval: 10000,
  pingTimeout: 20000,
});

app.use(express.static(path.join(__dirname, "public")));

const rooms = new Map();

io.on("connection", (socket) => {
  console.log("Client connected:", socket.id);

  socket.on("join-room", ({ room }) => {
    socket.join(room);

    if (!rooms.has(room)) {
      rooms.set(room, new Set());
    }
    rooms.get(room).add(socket.id);

    console.log("join-room:", room, "socket:", socket.id);
    socket.to(room).emit("user-joined", { socketId: socket.id });
  });

  socket.on("offer", ({ room, sdp, type }) => {
    console.log("offer received in room:", room);
    socket.to(room).emit("offer", { sdp, type, from: socket.id });
  });

  socket.on("answer", ({ room, sdp, type }) => {
    console.log("answer received in room:", room);
    socket.to(room).emit("answer", { sdp, type, from: socket.id });
  });

  socket.on("ice-candidate", ({ room, candidate, sdpMid, sdpMLineIndex }) => {
    console.log("ice-candidate received in room:", room);
    socket.to(room).emit("ice-candidate", { candidate, sdpMid, sdpMLineIndex, from: socket.id });
  });

  socket.on("leave-room", ({ room }) => {
    socket.leave(room);
    if (rooms.has(room)) {
      rooms.get(room).delete(socket.id);
      if (rooms.get(room).size === 0) {
        rooms.delete(room);
      }
    }
    socket.to(room).emit("user-left", { socketId: socket.id });
    console.log("leave-room:", room, "socket:", socket.id);
  });

  socket.on("disconnect", () => {
    console.log("Client disconnected:", socket.id);
    for (const [room, members] of rooms.entries()) {
      if (members.has(socket.id)) {
        members.delete(socket.id);
        socket.to(room).emit("user-left", { socketId: socket.id });
        if (members.size === 0) {
          rooms.delete(room);
        }
        break;
      }
    }
  });
});

const PORT = 3000;
server.listen(PORT, "0.0.0.0", () => {
  console.log(`Server running on port ${PORT}`);
});
