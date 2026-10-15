import QtQuick

// A frosted glass panel: the nebula seen through it, softened, inside a rounded rectangle with a faint rim.
Item {
    id: panel
    property int radius: app.radius + 4
    property real glow: 0
    default property alias contentData: inner.data

    Nebula {
        anchors.fill: parent
        frost: 1
        radius: panel.radius
        stars: 0
        // where this panel is in the window (recomputed as things move)
        itemPos: {
            app.time; app.frameWidth; app.frameHeight; panel.width; panel.height; app.layoutTick
            return panel.mapToItem(app.frameRoot, 0, 0)
        }
    }
    Rectangle {
        anchors.fill: parent
        radius: panel.radius
        color: "transparent"
        border.width: 1
        border.color: Qt.rgba(1, 1, 1, 0.075 + 0.12 * panel.glow)
    }
    Rectangle {
        x: panel.radius
        y: 0.5
        width: Math.max(0, parent.width - 2 * panel.radius)
        height: 1
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(1, 1, 1, 0) }
            GradientStop { position: 0.5; color: Qt.rgba(1, 1, 1, 0.16) }
            GradientStop { position: 1; color: Qt.rgba(1, 1, 1, 0) }
        }
    }
    Item {
        id: inner
        anchors.fill: parent
    }
}
