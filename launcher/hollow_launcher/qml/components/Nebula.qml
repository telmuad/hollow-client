import QtQuick

// The nebula shader (shaders/nebula.frag). frost = 0 draws the window background, frost = 1 a frosted glass panel
// that samples the same nebula at its own place in the window.
ShaderEffect {
    id: fx
    property real time: app.time
    property real frost: 0
    property real radius: 0
    property point mouse: app.mouse
    property point itemPos: Qt.point(0, 0)
    property size itemSize: Qt.size(width, height)
    property size windowSize: Qt.size(app.frameWidth, app.frameHeight)
    property color colorA: app.start
    property color colorB: app.end
    property color base: app.base
    property real stars: 1
    // resolved here: a plain relative path would be taken relative to whichever file creates the Nebula
    fragmentShader: Qt.resolvedUrl("../shaders/nebula.frag.qsb")
}
