import QtQuick

// The version profile picker: one pill per profile, a springy marker slides to the selected one.
Item {
    id: sw
    readonly property var profiles: backend.profileList
    readonly property int selected: {
        for (var i = 0; i < profiles.length; i++)
            if (profiles[i].id === backend.profileId) return i
        return 0
    }
    readonly property real pillW: 188
    implicitWidth: profiles.length * pillW + 8
    implicitHeight: 52
    opacity: backend.isBusy || backend.gameRunning ? 0.55 : 1

    Rectangle {
        anchors.fill: parent
        radius: app.radius + 6
        color: Qt.rgba(1, 1, 1, 0.04)
        border.width: 1
        border.color: Qt.rgba(1, 1, 1, 0.08)
    }
    Rectangle {
        id: marker
        x: 4 + sw.selected * sw.pillW
        y: 4
        width: sw.pillW
        height: sw.height - 8
        radius: app.radius + 3
        Behavior on x { SpringAnimation { spring: 3.6; damping: 0.32; epsilon: 0.2 } }
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0; color: Qt.rgba(app.start.r, app.start.g, app.start.b, 0.32) }
            GradientStop { position: 1; color: Qt.rgba(app.end.r, app.end.g, app.end.b, 0.12) }
        }
        border.width: 1
        border.color: Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.4)
    }
    Repeater {
        model: sw.profiles
        Item {
            objectName: "profile_" + modelData.id
            x: 4 + index * sw.pillW
            y: 4
            width: sw.pillW
            height: sw.height - 8
            readonly property bool active: index === sw.selected
            Column {
                anchors.verticalCenter: parent.verticalCenter
                x: 14
                spacing: 1
                Text {
                    text: modelData.name
                    color: active ? app.text : app.dim
                    font.family: app.fontFamily
                    font.pixelSize: 13
                    font.weight: Font.Bold
                }
                Text {
                    text: modelData.tagline + (modelData.installed ? "" : "  ·  not installed")
                    color: active ? app.accent : app.faint
                    font.family: app.fontFamily
                    font.pixelSize: 11
                }
            }
            HoverHandler {
                cursorShape: backend.isBusy || backend.gameRunning ? Qt.ArrowCursor : Qt.PointingHandCursor
            }
            TapHandler {
                enabled: !backend.isBusy && !backend.gameRunning && !active
                onTapped: {
                    sounds.play("click")
                    backend.selectProfile(modelData.id)
                }
            }
        }
    }
}
