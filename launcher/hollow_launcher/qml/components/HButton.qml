import QtQuick

// Hollow's button: springs when pressed, glows on hover, ripples where you click, plays a soft click.
Item {
    id: btn
    property string text: ""
    property string icon: ""
    property bool primary: false
    property bool danger: false
    property bool small: false
    property bool hoverSound: false
    signal clicked()

    implicitWidth: row.implicitWidth + (small ? 22 : 34)
    implicitHeight: small ? 28 : 36
    opacity: enabled ? 1 : 0.42
    scale: tap.pressed ? 0.955 : (hover.hovered && enabled ? 1.025 : 1)
    Behavior on scale { SpringAnimation { spring: 5; damping: 0.32; epsilon: 0.002 } }

    readonly property real hoverT: hover.hovered && enabled ? 1 : 0
    property real h: hoverT
    Behavior on h { NumberAnimation { duration: 140 / app.speed } }

    Rectangle {
        id: glow
        anchors.fill: bg
        anchors.margins: -3
        radius: bg.radius + 3
        color: "transparent"
        border.width: 3
        border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.22 * btn.h * theme.glow)
    }
    Rectangle {
        id: bg
        anchors.fill: parent
        radius: Math.min(height / 2, app.radius + 3)
        clip: true
        color: btn.danger ? Qt.rgba(0.55 + 0.2 * btn.h, 0.12, 0.13, 0.55 + 0.3 * btn.h)
                          : Qt.rgba(1, 1, 1, 0.055 + 0.075 * btn.h)
        border.width: 1
        border.color: btn.primary ? "transparent" : Qt.rgba(1, 1, 1, 0.09 + 0.12 * btn.h)
        gradient: btn.primary ? primaryGradient : null
        property Gradient primaryGradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.lighter(app.start, 1 + 0.18 * btn.h) }
            GradientStop { position: 1; color: Qt.lighter(app.end, 1 + 0.18 * btn.h) }
        }
        Ripple { id: ripple; anchors.fill: parent }
    }
    Row {
        id: row
        anchors.centerIn: parent
        spacing: 7
        Icon {
            visible: btn.icon !== ""
            name: btn.icon
            size: btn.small ? 14 : 16
            color: app.text
            anchors.verticalCenter: parent.verticalCenter
        }
        Text {
            text: btn.text
            visible: text !== ""
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: btn.small ? 12 : 14
            font.weight: btn.primary ? Font.Bold : Font.DemiBold
            anchors.verticalCenter: parent.verticalCenter
        }
    }
    HoverHandler {
        id: hover
        cursorShape: btn.enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
        onHoveredChanged: if (hovered && btn.enabled && btn.hoverSound) sounds.play("hover")
    }
    TapHandler {
        id: tap
        enabled: btn.enabled
        onTapped: (eventPoint) => {
            ripple.burst(eventPoint.position.x, eventPoint.position.y)
            sounds.play("click")
            btn.clicked()
        }
    }
}
