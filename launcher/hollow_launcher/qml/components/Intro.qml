import QtQuick

// The intro: scattered particles stream into the shape of the Hollow emblem, its crystal facets fly in and lock
// together, the H in the middle hollows out, the edges and glow light up and the wordmark appears; then everything
// fades into the launcher. Click or press any key to skip.
Item {
    id: intro
    anchors.fill: parent
    signal done()
    property bool running: false
    property bool leaving: false
    property real t: 0          // seconds since the start
    readonly property real size: Math.min(width, height) * 0.4
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
    // overshoots a little before settling, for pieces that snap into place
    function backOut(x) {
        var c1 = 1.70158, c3 = c1 + 1, u = x - 1
        return 1 + c3 * u * u * u + c1 * u * u
    }
    function clamp01(x) { return Math.max(0, Math.min(1, x)) }

    Timer {
        id: clock
        interval: 16
        repeat: true
        onTriggered: {
            intro.t += 0.016 * app.speed
            if (intro.t > 2.95 && !intro.leaving) intro.skip()
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
        anchors.verticalCenterOffset: -intro.size * 0.12
        width: intro.size
        height: intro.size

        // 1. particles stream in from a wide ring and fill the crystal's shape
        Repeater {
            model: introPoints
            Rectangle {
                readonly property real angle: index * 2.399963 + (index % 7) * 0.31
                readonly property real far: intro.size * (1.1 + ((index * 37) % 100) / 100 * 1.4)
                readonly property real delay: ((index * 53) % 100) / 100 * 0.4
                readonly property real p: intro.clamp01((intro.t - delay) / 0.95)
                readonly property real e: 1 - Math.pow(1 - p, 3)
                readonly property real tx: modelData[0] * intro.size
                readonly property real ty: modelData[1] * intro.size
                width: 3.2 + 2.5 * (1 - e)
                height: width
                radius: width / 2
                x: stage.width / 2 + Math.cos(angle) * far * (1 - e) + tx * e - width / 2
                y: stage.height / 2 + Math.sin(angle) * far * (1 - e) + ty * e - height / 2
                color: Qt.tint(app.start, Qt.rgba(app.end.r, app.end.g, app.end.b, modelData[0] + 0.5))
                // the particles hand over to the facets as those arrive
                opacity: Math.min(1, p * 3) * (1 - intro.clamp01((intro.t - 1.15) / 0.4))
            }
        }

        // 2. the emblem's own layers
        Item {
            id: emblem
            anchors.fill: parent
            readonly property real assemble: intro.clamp01((intro.t - 0.95) / 0.65)
            readonly property real hollow: intro.clamp01((intro.t - 1.6) / 0.35)

            Image {
                anchors.centerIn: parent
                width: parent.width * 1.04
                height: width
                source: emblemDir + "glow.png"
                smooth: true
                mipmap: true
                opacity: emblem.hollow * (0.78 + 0.22 * Math.sin(intro.t * 4.2))
            }
            Repeater {
                model: emblemFacets
                Image {
                    id: facet
                    readonly property real p: intro.clamp01((emblem.assemble * 1.45 - index * 0.065) / 0.7)
                    readonly property real e: intro.backOut(p)
                    readonly property real dx: (modelData[0] - 0.5) * emblem.width
                    readonly property real dy: (modelData[1] - 0.5) * emblem.height
                    width: emblem.width
                    height: emblem.height
                    source: emblemDir + "facet_" + index + ".png"
                    smooth: true
                    mipmap: true
                    // each facet flies in from beyond its own side, turning into place
                    x: dx * (1 - e) * 1.7
                    y: dy * (1 - e) * 1.7
                    opacity: Math.min(1, p * 2.2)
                    transform: Rotation {
                        origin.x: modelData[0] * facet.width
                        origin.y: modelData[1] * facet.height
                        angle: (1 - facet.e) * (index % 2 ? -50 : 50)
                    }
                }
            }
            Image {
                anchors.fill: parent
                source: emblemDir + "edge.png"
                smooth: true
                mipmap: true
                opacity: emblem.assemble * emblem.assemble
            }
            // the H starts filled in, then hollows out
            Image {
                anchors.fill: parent
                source: emblemDir + "hole_fill.png"
                smooth: true
                mipmap: true
                opacity: intro.clamp01((emblem.assemble - 0.45) / 0.3) * (1 - emblem.hollow)
            }
            Image {
                anchors.fill: parent
                source: emblemDir + "hole_rim.png"
                smooth: true
                mipmap: true
                opacity: emblem.hollow
            }
            Image {
                anchors.fill: parent
                source: emblemDir + "spark.png"
                smooth: true
                mipmap: true
                opacity: emblem.hollow * (0.7 + 0.3 * Math.sin(intro.t * 7))
            }
        }
    }
    // 3. the wordmark
    Image {
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.top: stage.bottom
        anchors.topMargin: intro.size * 0.1
        source: wordmarkImage
        smooth: true
        mipmap: true
        height: Math.max(40, intro.size * 0.3)
        fillMode: Image.PreserveAspectFit
        readonly property real k: intro.clamp01((intro.t - 1.75) / 0.6)
        opacity: k
        scale: 0.94 + 0.06 * k
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
