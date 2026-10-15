import QtQuick

// Click ripples: call burst(x, y) and a soft circle grows from that point and fades.
Item {
    id: root
    clip: true
    property color color: Qt.rgba(1, 1, 1, 0.28)

    function burst(px, py) {
        wave.createObject(root, { cx: px, cy: py })
    }

    Component {
        id: wave
        Rectangle {
            id: w
            property real cx: 0
            property real cy: 0
            property real size: 0
            x: cx - size / 2
            y: cy - size / 2
            width: size
            height: size
            radius: size / 2
            color: root.color
            NumberAnimation on size {
                from: 0
                to: Math.max(root.width, root.height) * 2.6
                duration: 560 / app.speed
                easing.type: Easing.OutCubic
            }
            NumberAnimation on opacity {
                from: 1
                to: 0
                duration: 600 / app.speed
                easing.type: Easing.InQuad
                onFinished: w.destroy()
            }
        }
    }
}
