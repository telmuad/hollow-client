import QtQuick
import "../components"

// Logs: the launcher's messages and the game's output as it runs, colored by kind.
Item {
    id: page
    property real appear: 1
    property bool follow: true

    Row {
        id: tools
        spacing: 8
        opacity: page.appear
        HButton { small: true; icon: "copy"; text: "Copy"; onClicked: backend.copyLogs() }
        HButton { small: true; icon: "trash"; text: "Clear"; onClicked: backend.clearLogs() }
        HButton { small: true; icon: "folder"; text: "Logs folder"; onClicked: backend.openFolder("logs") }
    }
    HToggle {
        anchors.right: parent.right
        anchors.verticalCenter: tools.verticalCenter
        width: 190
        text: "Follow new lines"
        checked: page.follow
        onToggled: (v) => page.follow = v
    }

    GlassPanel {
        anchors.top: tools.bottom
        anchors.topMargin: 14
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        opacity: page.appear
        transform: Translate { y: 16 * (1 - page.appear) }

        ListView {
            id: list
            anchors.fill: parent
            anchors.margins: 14
            clip: true
            model: backend.logModel
            boundsBehavior: Flickable.StopAtBounds
            delegate: Text {
                width: list.width
                text: model.text
                color: model.kind === "error" ? "#ff9b97" : (model.kind === "warn" ? "#f5c56b" : (model.kind === "hollow" ? app.accent : "#c9ccd8"))
                font.family: "Consolas"
                font.pixelSize: 12
                wrapMode: Text.WrapAnywhere
            }
            onCountChanged: if (page.follow) Qt.callLater(list.positionViewAtEnd)
        }
        Text {
            anchors.centerIn: parent
            visible: list.count === 0
            text: "Nothing here yet."
            color: app.faint
            font.family: app.fontFamily
            font.pixelSize: 13
        }
    }
}
