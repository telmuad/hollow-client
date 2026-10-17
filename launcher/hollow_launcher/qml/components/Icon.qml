import QtQuick
import QtQuick.Shapes

// Small line icons drawn from paths (all made for Hollow; 18 x 18 grid).
Shape {
    id: icon
    property string name: "home"
    property color color: app.dim
    property real stroke: 1.6
    readonly property var paths: ({
        "home": "M3 9 L9 3.5 L15 9 M5 7.6 L5 15 L13 15 L13 7.6 M7.6 15 L7.6 11 L10.4 11 L10.4 15",
        "wardrobe": "M9 3 C10.4 3 11.5 4.1 11.5 5.5 C11.5 6.9 10.4 8 9 8 C7.6 8 6.5 6.9 6.5 5.5 C6.5 4.1 7.6 3 9 3 Z M3.8 15.5 C3.8 11.8 6.1 9.6 9 9.6 C11.9 9.6 14.2 11.8 14.2 15.5",
        "stats": "M3.5 15 L3.5 10 M7.2 15 L7.2 5.5 M10.9 15 L10.9 8 M14.6 15 L14.6 3.5 M2 15.6 L16 15.6",
        "settings": "M3 5 L15 5 M3 9 L15 9 M3 13 L15 13 M6 3.4 L6 6.6 M12 7.4 L12 10.6 M8 11.4 L8 14.6",
        "logs": "M4 4 L14 4 M4 7.4 L14 7.4 M4 10.8 L11.5 10.8 M4 14.2 L9 14.2",
        "minimize": "M4.5 9 L13.5 9",
        "maximize": "M4.5 4.5 L13.5 4.5 L13.5 13.5 L4.5 13.5 Z",
        "restore": "M6.5 6.5 L13.5 6.5 L13.5 13.5 L6.5 13.5 Z M4.5 11 L4.5 4.5 L11 4.5",
        "close": "M4.8 4.8 L13.2 13.2 M13.2 4.8 L4.8 13.2",
        "folder": "M2.5 5 L7 5 L8.5 6.6 L15.5 6.6 L15.5 14 L2.5 14 Z",
        "refresh": "M14.5 9 C14.5 12 12 14.5 9 14.5 C6 14.5 3.5 12 3.5 9 C3.5 6 6 3.5 9 3.5 C10.8 3.5 12.4 4.4 13.4 5.8 M13.6 2.8 L13.6 6 L10.4 6",
        "user": "M9 2.8 C10.6 2.8 11.8 4 11.8 5.6 C11.8 7.2 10.6 8.4 9 8.4 C7.4 8.4 6.2 7.2 6.2 5.6 C6.2 4 7.4 2.8 9 2.8 Z M3.5 15.4 C3.8 12.2 6 10.2 9 10.2 C12 10.2 14.2 12.2 14.5 15.4",
        "wrench": "M11.5 3 C13.4 3 15 4.6 15 6.5 C15 7 14.9 7.4 14.7 7.8 L12.5 5.6 L11 7 L13.2 9.2 C12.8 9.4 12.4 9.5 11.9 9.5 C11.5 9.5 11.1 9.4 10.7 9.3 L4.6 15.4 C4 16 3 16 2.6 15.4 C2 14.9 2 14 2.6 13.4 L8.7 7.3 C8.6 6.9 8.5 6.5 8.5 6.1 C8.5 4.4 9.8 3 11.5 3 Z",
        "copy": "M6 6 L14 6 L14 15 L6 15 Z M4 12 L4 3 L11 3",
        "trash": "M3.5 5 L14.5 5 M7 5 L7 3.4 L11 3.4 L11 5 M5 5 L5.8 15 L12.2 15 L13 5",
        "check": "M3.8 9.4 L7.4 13 L14.2 5.4",
        "music": "M7 13.5 C7 14.6 6.1 15.4 5 15.4 C3.9 15.4 3 14.6 3 13.5 C3 12.4 3.9 11.6 5 11.6 C6.1 11.6 7 12.4 7 13.5 Z M7 13.5 L7 4 L15 2.6 L15 11.8 M15 11.8 C15 12.9 14.1 13.7 13 13.7 C11.9 13.7 11 12.9 11 11.8 C11 10.7 11.9 9.9 13 9.9 C14.1 9.9 15 10.7 15 11.8 Z",
        "image": "M2.5 3.5 L15.5 3.5 L15.5 14.5 L2.5 14.5 Z M2.5 12 L6.5 8 L10 11.5 L12 9.5 L15.5 13 M11.8 6.4 L11.9 6.4",
        "globe": "M9 2.5 C12.6 2.5 15.5 5.4 15.5 9 C15.5 12.6 12.6 15.5 9 15.5 C5.4 15.5 2.5 12.6 2.5 9 C2.5 5.4 5.4 2.5 9 2.5 Z M2.5 9 L15.5 9 M9 2.5 C7 4.6 6.2 6.8 6.2 9 C6.2 11.2 7 13.4 9 15.5 C11 13.4 11.8 11.2 11.8 9 C11.8 6.8 11 4.6 9 2.5",
        "island": "M2.5 8.6 L15.5 8.6 L13 11.6 L10.4 12.4 L9 15.6 L7.6 12.4 L5 11.6 Z M9 8.6 L9 4.6 M9 4.6 C7.4 4.6 6.2 3.6 6.3 2.4 C7.8 2.4 8.9 3.3 9 4.6 M9 5.4 C10.4 5.4 11.6 4.4 11.7 3.2 C10.2 3.2 9.1 4.1 9 5.4",
        "spark": "M9 2 L10.4 7.6 L16 9 L10.4 10.4 L9 16 L7.6 10.4 L2 9 L7.6 7.6 Z"
    })
    // the icon's size in pixels (set this, not width/height: a Shape sizes itself from its path)
    property real size: 18
    width: size
    height: size
    readonly property real iconScale: size / 18
    preferredRendererType: Shape.CurveRenderer
    ShapePath {
        strokeColor: icon.color
        strokeWidth: icon.stroke
        fillColor: "transparent"
        capStyle: ShapePath.RoundCap
        joinStyle: ShapePath.RoundJoin
        scale: Qt.size(icon.iconScale, icon.iconScale)
        PathSvg { path: icon.paths[icon.name] || "" }
    }
}
