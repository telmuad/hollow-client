import QtQuick

// A slider with a gradient fill and a knob that grows while you drag. `moved` fires while dragging, `committed`
// when you let go.
Item {
    id: s
    property real from: 0
    property real to: 100
    property real stepSize: 1
    property real value: 0
    property real shown: value
    signal moved(real value)
    signal committed(real value)

    implicitWidth: 320
    implicitHeight: 26

    readonly property real frac: to > from ? (shown - from) / (to - from) : 0

    function valueAt(px) {
        var f = Math.max(0, Math.min(1, px / Math.max(1, track.width)))
        var v = from + f * (to - from)
        return Math.round(v / stepSize) * stepSize
    }

    Rectangle {
        id: track
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width
        height: 6
        radius: 3
        color: "#2b2e3b"
        Rectangle {
            width: Math.max(6, track.width * s.frac)
            height: parent.height
            radius: 3
            gradient: Gradient {
                orientation: Gradient.Horizontal
                GradientStop { position: 0; color: app.start }
                GradientStop { position: 1; color: app.end }
            }
        }
    }
    Rectangle {
        id: knob
        property real size: drag.pressed ? 20 : (hover.hovered ? 17 : 15)
        Behavior on size { SpringAnimation { spring: 6; damping: 0.4; epsilon: 0.1 } }
        width: size
        height: size
        radius: size / 2
        x: track.width * s.frac - size / 2
        anchors.verticalCenter: parent.verticalCenter
        color: "#f4f4fa"
        border.width: 3
        border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.55)
    }
    HoverHandler { id: hover; cursorShape: Qt.PointingHandCursor }
    MouseArea {
        id: drag
        anchors.fill: parent
        onPressed: (m) => { s.shown = s.valueAt(m.x); s.moved(s.shown) }
        onPositionChanged: (m) => { if (pressed) { s.shown = s.valueAt(m.x); s.moved(s.shown) } }
        onReleased: { sounds.play("click"); s.committed(s.shown) }
    }
    onValueChanged: if (!drag.pressed) shown = value
}
