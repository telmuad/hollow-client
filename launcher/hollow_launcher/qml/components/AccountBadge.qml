import QtQuick

// Your face (from your skin), name and sign-in state. Click for the account menu.
Item {
    id: badge
    implicitHeight: 58
    property bool menuOpen: false

    GlassPanel {
        anchors.fill: parent
        radius: app.radius + 4
        glow: hh.hovered ? 1 : 0
    }
    Item {
        id: face
        x: 10
        anchors.verticalCenter: parent.verticalCenter
        width: 36
        height: 36
        Rectangle {
            anchors.fill: parent
            radius: 8
            color: Qt.rgba(1, 1, 1, 0.06)
            visible: backend.skinUrl === ""
            Icon {
                anchors.centerIn: parent
                name: "user"
                color: app.dim
            }
        }
        Image {
            anchors.fill: parent
            visible: backend.skinUrl !== ""
            source: backend.skinUrl
            sourceClipRect: Qt.rect(8, 8, 8, 8)
            smooth: false
        }
        Image {
            anchors.fill: parent
            anchors.margins: -2
            visible: backend.skinUrl !== ""
            source: backend.skinUrl
            sourceClipRect: Qt.rect(40, 8, 8, 8)
            smooth: false
        }
        Rectangle {
            width: 10
            height: 10
            radius: 5
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.margins: -3
            color: backend.signedIn ? "#5bd18b" : (backend.accountName !== "" ? "#f5a524" : "#676c7c")
            border.width: 2
            border.color: app.base
        }
    }
    Column {
        anchors.left: face.right
        anchors.leftMargin: 11
        anchors.right: parent.right
        anchors.rightMargin: 10
        anchors.verticalCenter: parent.verticalCenter
        spacing: 1
        Text {
            width: parent.width
            text: backend.accountName !== "" ? backend.accountName : "Not signed in"
            color: app.text
            font.family: app.fontFamily
            font.pixelSize: 13
            font.weight: Font.DemiBold
            elide: Text.ElideRight
        }
        Text {
            width: parent.width
            text: backend.accountNote !== "" ? backend.accountNote : (backend.signedIn ? "Microsoft account" : (backend.accountName !== "" ? "signs in on PLAY" : "click to sign in"))
            color: app.faint
            font.family: app.fontFamily
            font.pixelSize: 11
            elide: Text.ElideRight
        }
    }
    HoverHandler { id: hh; cursorShape: Qt.PointingHandCursor }
    TapHandler {
        onTapped: {
            sounds.play("click")
            app.openAccountMenu(badge)
        }
    }
}
