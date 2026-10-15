import QtQuick

// The intro: scattered particles fly together into the H sigil, the logo and name light up, then everything
// fades into the launcher. Click or press any key to skip.
Item {
    id: intro
    anchors.fill: parent
    signal done()
    property bool running: false
    property bool leaving: false
    property real t: 0          // seconds since the start
    readonly property real size: Math.min(width, height) * 0.36
    focus: running

    function start() {
        running = true
        t = 0
        forceActiveFocus()
        sounds.play("intro")
        clock.start()
    }
    function skip() {
        if (!running || leaving) return
        leaving = true
        out.start()
    }

    Timer {
        id: clock
        interval: 16
        repeat: true
        onTriggered: {
            intro.t += 0.016 * app.speed
            if (intro.t > 2.55 && !intro.leaving) intro.skip()
        }
    }
    SequentialAnimation {
        id: out
        ParallelAnimation {
            NumberAnimation { target: intro; property: "opacity"; to: 0; duration: 520; easing.type: Easing.InQuad }
            NumberAnimation { target: stage; property: "scale"; to: 1.12; duration: 520; easing.type: Easing.InQuad }
        }
        ScriptAction {
            script: {
                clock.stop()
                intro.running = false
                intro.visible = false
                intro.done()
            }
        }
    }

    Rectangle {
        anchors.fill: parent
        color: app.base
        radius: app.corner
    }
    Nebula {
        anchors.fill: parent
        radius: app.corner
        opacity: Math.min(1, intro.t / 1.2) * 0.9
    }

    Item {
        id: stage
        anchors.centerIn: parent
        width: intro.size
        height: intro.size

        Repeater {
            model: introPoints
            Rectangle {
                id: dot
                // where it flies from (a wide ring around the logo) and when
                readonly property real angle: index * 2.399963 + (index % 7) * 0.31
                readonly property real far: intro.size * (1.1 + ((index * 37) % 100) / 100 * 1.4)
                readonly property real delay: ((index * 53) % 100) / 100 * 0.45
                readonly property real p: Math.max(0, Math.min(1, (intro.t - delay) / 1.05))
                readonly property real e: 1 - Math.pow(1 - p, 3)
                readonly property real tx: modelData[0] * intro.size
                readonly property real ty: modelData[1] * intro.size
                width: 3.2 + 2.5 * (1 - e)
                height: width
                radius: width / 2
                x: stage.width / 2 + Math.cos(angle) * far * (1 - e) + tx * e - width / 2
                y: stage.height / 2 + Math.sin(angle) * far * (1 - e) + ty * e - height / 2
                color: Qt.tint(app.start, Qt.rgba(app.end.r, app.end.g, app.end.b, modelData[0] + 0.5))
                opacity: Math.min(1, p * 3) * (intro.t > 1.35 ? Math.max(0, 1 - (intro.t - 1.35) / 0.4) : 1)
            }
        }
        Image {
            id: sigil
            anchors.centerIn: parent
            width: intro.size
            height: intro.size
            source: app.sigilUrl
            smooth: true
            mipmap: true
            opacity: Math.max(0, Math.min(1, (intro.t - 1.15) / 0.35))
            scale: 0.92 + 0.08 * opacity
        }
    }
    Text {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: stage.bottom
        anchors.topMargin: intro.size * 0.12
        text: "HOLLOW CLIENT"
        color: app.text
        font.family: app.fontFamily
        font.pixelSize: Math.max(18, intro.size * 0.11)
        font.weight: Font.Bold
        readonly property real k: Math.max(0, Math.min(1, (intro.t - 1.35) / 0.6))
        font.letterSpacing: 4 + 18 * (1 - k)
        opacity: k
    }
    Text {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 26
        text: "click to skip"
        color: app.faint
        font.family: app.fontFamily
        font.pixelSize: 12
        opacity: intro.t > 0.4 ? 0.7 : 0
        Behavior on opacity { NumberAnimation { duration: 400 } }
    }
    MouseArea {
        anchors.fill: parent
        enabled: intro.running
        onClicked: intro.skip()
    }
    Keys.onPressed: (event) => { intro.skip(); event.accepted = true }
}
