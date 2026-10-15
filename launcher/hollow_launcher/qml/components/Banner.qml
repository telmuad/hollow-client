import QtQuick

// A message strip (updates available, sign-in not approved, ...) that slides in and can be dismissed.
Item {
    id: b
    property string text: backend.bannerText
    property string kind: backend.bannerKind
    property string action: backend.bannerAction
    readonly property bool shown: text !== ""
    implicitHeight: shown ? content.implicitHeight + 20 : 0
    clip: true
    Behavior on implicitHeight { SpringAnimation { spring: 4; damping: 0.45; epsilon: 0.3 } }
    opacity: shown ? 1 : 0
    Behavior on opacity { NumberAnimation { duration: 200 } }

    Rectangle {
        anchors.fill: parent
        radius: app.radius + 2
        color: b.kind === "warn" ? Qt.rgba(0.96, 0.62, 0.04, 0.13) : Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.14)
        border.width: 1
        border.color: b.kind === "warn" ? Qt.rgba(0.96, 0.62, 0.04, 0.45) : Qt.rgba(app.accent.r, app.accent.g, app.accent.b, 0.45)
    }
    Row {
        id: content
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.leftMargin: 14
        anchors.rightMargin: 10
        anchors.verticalCenter: parent.verticalCenter
        spacing: 10
        Icon {
            name: b.kind === "warn" ? "globe" : "spark"
            color: b.kind === "warn" ? "#f5a524" : app.accent
            anchors.verticalCenter: parent.verticalCenter
        }
        Text {
            width: content.width - 18 - (actionBtn.visible ? actionBtn.width + 10 : 0) - closeBtn.width - 30
            text: b.text
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 13
            wrapMode: Text.WordWrap
            anchors.verticalCenter: parent.verticalCenter
        }
        HButton {
            id: actionBtn
            visible: b.action !== ""
            small: true
            primary: true
            text: b.action
            anchors.verticalCenter: parent.verticalCenter
            onClicked: backend.bannerClicked()
        }
        HButton {
            id: closeBtn
            small: true
            icon: "close"
            anchors.verticalCenter: parent.verticalCenter
            onClicked: backend.dismissBanner()
        }
    }
}
