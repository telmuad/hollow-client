import QtQuick
import QtQuick.Shapes
import QtQuick.Effects

// The PLAY button. It breathes while waiting; when pressed a ring charges around it, then it bursts into
// particles and `fired` is emitted.
Item {
    id: play
    property string label: "PLAY"
    property string sub: ""
    property bool ready: true
    signal fired()
    objectName: "playButton"

    /** Start the charge-and-burst as if clicked (used by the screenshot run). */
    function chargeUp() { if (ready && !charging) fire.start() }

    implicitWidth: 300
    implicitHeight: 84

    property real charge: 0
    property bool charging: false
    property real breathe: 0
    SequentialAnimation on breathe {
        running: play.ready && !play.charging
        loops: Animation.Infinite
        NumberAnimation { from: 0; to: 1; duration: 1500 / app.speed; easing.type: Easing.InOutSine }
        NumberAnimation { from: 1; to: 0; duration: 1500 / app.speed; easing.type: Easing.InOutSine }
    }
    readonly property real hoverT: hover.hovered && ready ? 1 : 0
    property real h: hoverT
    Behavior on h { NumberAnimation { duration: 160 / app.speed } }

    scale: tap.pressed && ready ? 0.965 : 1 + 0.03 * h
    Behavior on scale { SpringAnimation { spring: 4.5; damping: 0.3; epsilon: 0.002 } }

    RectangularShadow {
        anchors.fill: body
        radius: body.radius
        blur: 26 + 18 * play.breathe + 20 * play.charge
        spread: 2
        color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, play.ready ? (0.32 + 0.25 * play.breathe + 0.3 * play.h) * theme.glow + 0.15 : 0.1)
    }
    Rectangle {
        id: body
        anchors.fill: parent
        radius: Math.min(height / 2, app.radius + 14)
        opacity: play.ready ? 1 : 0.55
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.lighter(app.start, 1 + 0.15 * play.h + 0.3 * play.charge) }
            GradientStop { position: 1; color: Qt.lighter(app.end, 1 + 0.15 * play.h + 0.3 * play.charge) }
        }
        clip: true
        // a sheen that sweeps across now and then
        Rectangle {
            id: sheen
            width: parent.width * 0.35
            height: parent.height * 2
            y: -parent.height / 2
            rotation: 18
            opacity: 0.22
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0) }
                GradientStop { position: 0.5; color: Qt.rgba(1, 1, 1, 0.9) }
                GradientStop { position: 1; color: Qt.rgba(1, 1, 1, 0) }
            }
            SequentialAnimation on x {
                running: play.ready
                loops: Animation.Infinite
                NumberAnimation { from: -sheen.width * 1.5; to: body.width + sheen.width; duration: 1400 / app.speed; easing.type: Easing.InOutQuad }
                PauseAnimation { duration: 2600 / app.speed }
            }
        }
        Ripple { id: ripple; anchors.fill: parent }
    }
    Column {
        anchors.centerIn: parent
        spacing: 1
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: play.label
            color: "#ffffff"
            font.family: app.fontFamily
            font.pixelSize: 30
            font.weight: Font.Bold
            font.letterSpacing: 6 + 2 * play.h
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            visible: play.sub !== ""
            text: play.sub
            color: Qt.rgba(1, 1, 1, 0.82)
            font.family: app.fontFamily
            font.pixelSize: 12
        }
    }
    // charge ring around the button
    Shape {
        id: ring
        anchors.fill: body
        anchors.margins: -7
        visible: play.charge > 0
        preferredRendererType: Shape.CurveRenderer
        ShapePath {
            strokeColor: "#ffffff"
            strokeWidth: 3
            fillColor: "transparent"
            capStyle: ShapePath.RoundCap
            PathRectangle {
                x: 1.5
                y: 1.5
                width: ring.width - 3
                height: ring.height - 3
                radius: (ring.height - 3) / 2
            }
        }
        layer.enabled: true
        layer.effect: MultiEffect {
            maskEnabled: true
            maskSource: chargeMask
            maskThresholdMin: 0.5
            maskSpreadAtMin: 0.2
        }
    }
    // a pie that grows with the charge; it masks the ring above
    Item {
        id: chargeMask
        anchors.fill: ring
        layer.enabled: true
        visible: false
        Shape {
            anchors.fill: parent
            ShapePath {
                fillColor: "white"
                strokeColor: "transparent"
                startX: chargeMask.width / 2
                startY: chargeMask.height / 2
                PathAngleArc {
                    centerX: chargeMask.width / 2
                    centerY: chargeMask.height / 2
                    radiusX: chargeMask.width
                    radiusY: chargeMask.width
                    startAngle: -90
                    sweepAngle: 360 * play.charge
                    moveToStart: false
                }
            }
        }
    }

    SequentialAnimation {
        id: fire
        ScriptAction { script: { play.charging = true; sounds.play("charge") } }
        NumberAnimation { target: play; property: "charge"; from: 0; to: 1; duration: 520 / app.speed; easing.type: Easing.InQuad }
        ScriptAction { script: { sounds.play("pop"); burst.explode() } }
        NumberAnimation { target: play; property: "charge"; to: 0; duration: 220 }
        ScriptAction { script: { play.charging = false; play.fired() } }
    }

    // the explosion: sparks thrown out from the button
    Item {
        id: burst
        anchors.centerIn: parent
        width: 1
        height: 1
        function explode() {
            for (var i = 0; i < 44; i++) {
                var a = Math.random() * Math.PI * 2
                var speed = 70 + Math.random() * 150
                spark.createObject(burst, {
                    dx: Math.cos(a) * speed * 1.6, dy: Math.sin(a) * speed,
                    startX: (Math.random() - 0.5) * play.width * 0.8, startY: (Math.random() - 0.5) * play.height * 0.5,
                    tint: Math.random() < 0.5 ? app.start : app.end, size: 3 + Math.random() * 5
                })
            }
        }
    }
    Component {
        id: spark
        Rectangle {
            id: sp
            property real dx: 0
            property real dy: 0
            property real startX: 0
            property real startY: 0
            property real t: 0
            property color tint: "white"
            property real size: 4
            width: size * (1 - t * 0.6)
            height: width
            radius: width / 2
            color: Qt.lighter(tint, 1.4)
            x: startX + dx * (1 - Math.pow(1 - t, 3)) - width / 2
            y: startY + dy * (1 - Math.pow(1 - t, 3)) + 40 * t * t - height / 2
            opacity: 1 - t
            NumberAnimation on t { from: 0; to: 1; duration: 700 + Math.random() * 400; onFinished: sp.destroy() }
        }
    }

    HoverHandler {
        id: hover
        cursorShape: play.ready ? Qt.PointingHandCursor : Qt.ArrowCursor
        onHoveredChanged: if (hovered && play.ready) sounds.play("hover")
    }
    TapHandler {
        id: tap
        enabled: play.ready && !play.charging
        onTapped: (p) => {
            ripple.burst(p.position.x, p.position.y)
            fire.start()
        }
    }
}
