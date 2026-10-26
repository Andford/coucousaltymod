// Tokkip Notas for Windows: one window, your pages in a local SQLite database
// (store.rs). No accounts, no internet: copies are JSON files you save where
// you like, the same format as the Android app's.

mod store;

use std::sync::Arc;
use store::{Db, Row};
use tauri::{Manager, State};
use tauri_plugin_dialog::DialogExt;

type Shared = Arc<Db>;

fn err<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}

/// Database work off the window's thread.
async fn on_db<T: Send + 'static>(db: &Shared, job: impl FnOnce(&Db) -> rusqlite::Result<T> + Send + 'static) -> Result<T, String> {
    let db = db.clone();
    tauri::async_runtime::spawn_blocking(move || job(&db)).await.map_err(err)?.map_err(err)
}

#[tauri::command]
async fn notas_load(db: State<'_, Shared>) -> Result<Vec<Row>, String> {
    on_db(&db, |d| d.load(store::now_ms())).await
}

#[tauri::command]
async fn notas_body(db: State<'_, Shared>, id: String) -> Result<String, String> {
    on_db(&db, move |d| d.body(&id)).await
}

#[tauri::command]
async fn notas_upsert(db: State<'_, Shared>, rows: Vec<Row>) -> Result<(), String> {
    on_db(&db, move |d| d.upsert(&rows)).await
}

#[tauri::command]
async fn notas_remove(db: State<'_, Shared>, ids: Vec<String>) -> Result<(), String> {
    on_db(&db, move |d| d.remove(&ids)).await
}

#[tauri::command]
async fn notas_dump(db: State<'_, Shared>) -> Result<Vec<Row>, String> {
    on_db(&db, |d| d.dump()).await
}

#[tauri::command]
async fn notas_bulk(db: State<'_, Shared>, rows: Vec<Row>, replace: bool) -> Result<(), String> {
    on_db(&db, move |d| d.bulk(&rows, replace)).await
}

#[tauri::command]
fn notas_where(db: State<'_, Shared>) -> String {
    db.path.display().to_string()
}

/// Asks where to save a copy and writes it there. The file's name, or None if cancelled.
#[tauri::command]
async fn notas_save_copy(app: tauri::AppHandle, json: String, name: String) -> Result<Option<String>, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Guardar una copia de tus notas")
        .set_file_name(&name)
        .add_filter("Copia de Tokkip Notas", &["json"])
        .blocking_save_file();
    let Some(path) = picked.and_then(|p| p.into_path().ok()) else { return Ok(None) };
    std::fs::write(&path, json).map_err(err)?;
    Ok(path.file_name().map(|n| n.to_string_lossy().into_owned()))
}

/// Asks for a copy and reads it (up to 64 MB). None if cancelled.
#[tauri::command]
async fn notas_open_copy(app: tauri::AppHandle) -> Result<Option<String>, String> {
    let picked = app
        .dialog()
        .file()
        .set_title("Abrir una copia de Tokkip Notas")
        .add_filter("Copia de Tokkip Notas", &["json"])
        .blocking_pick_file();
    let Some(path) = picked.and_then(|p| p.into_path().ok()) else { return Ok(None) };
    let size = std::fs::metadata(&path).map_err(err)?.len();
    if size > 64 * 1024 * 1024 {
        return Err("La copia es demasiado grande".into());
    }
    std::fs::read_to_string(&path).map(Some).map_err(err)
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            // A second launch brings the window you already have.
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.unminimize();
                let _ = w.show();
                let _ = w.set_focus();
            }
        }))
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let dir = app.path().app_data_dir()?;
            std::fs::create_dir_all(&dir)?;
            let path = dir.join("notas.db");
            store::daily_backup(&path, store::now_ms());
            let db = Db::open(&path).map_err(|e| Box::new(e) as Box<dyn std::error::Error>)?;
            app.manage(Arc::new(db));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            notas_load,
            notas_body,
            notas_upsert,
            notas_remove,
            notas_dump,
            notas_bulk,
            notas_where,
            notas_save_copy,
            notas_open_copy
        ])
        .run(tauri::generate_context!())
        .expect("Tokkip Notas could not start");
}
