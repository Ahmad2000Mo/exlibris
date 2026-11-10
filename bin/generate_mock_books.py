#!/usr/bin/env python3
import urllib.request
import urllib.parse
import json
import sys

def fetch_books():
    queries = ["computer", "science", "fiction", "history", "philosophy"]
    books = []
    seen_titles = set()
    
    print("Fetching from Open Library API...", flush=True)
    for q in queries:
        url = f"https://openlibrary.org/search.json?q={urllib.parse.quote(q)}&limit=300"
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req) as response:
                data = json.loads(response.read().decode('utf-8'))
                for doc in data.get('docs', []):
                    title = doc.get('title')
                    authors = doc.get('author_name')
                    if title and authors and title not in seen_titles:
                        seen_titles.add(title)
                        books.append({
                            'title': title,
                            'author': authors[0]
                        })
                    if len(books) >= 1000:
                        break
        except Exception as e:
            print(f"Error fetching for query {q}: {e}", file=sys.stderr)
        if len(books) >= 1000:
            break
            
    return books[:1000]

def main():
    books = fetch_books()
    if not books:
        print("No books could be fetched.", file=sys.stderr)
        sys.exit(1)
        
    print(f"Successfully fetched {len(books)} books.")
    
    # Process unique authors
    unique_authors = sorted(list(set(b['author'] for b in books)))
    author_id_map = {name: idx + 1 for idx, name in enumerate(unique_authors)}
    
    # Generate SQL file
    sql_file = 'mock_books.sql'
    with open(sql_file, 'w', encoding='utf-8') as f:
        f.write("-- Mock Book Data Generated from Open Library (bin/generate_mock_books.py)\n")
        # Kein TRUNCATE mit abgeschalteten Fremdschluesseln mehr: das hat bestehende Ausleihen
        # auf falsche Exemplare zeigen lassen. Die Tabellen werden vorher von createdb.sql neu angelegt.
        f.write("-- Erwartet frisch angelegte, leere Tabellen (createdb.sql).\n\n")
        
        # Insert unique authors
        f.write("-- Inserting Authors\n")
        author_values = []
        for name, aid in author_id_map.items():
            safe_name = name.replace("'", "''")
            author_values.append(f"({aid}, '{safe_name}')")
        
        for i in range(0, len(author_values), 100):
            chunk = author_values[i:i+100]
            f.write(f"INSERT INTO BIB_Autorin (id, name) VALUES {', '.join(chunk)};\n")
            
        # Insert books
        f.write("\n-- Inserting Books\n")
        book_values = []
        for bid, b in enumerate(books):
            safe_title = b['title'].replace("'", "''")
            book_values.append(f"({bid+1}, '{safe_title}')")
            
        for i in range(0, len(book_values), 100):
            chunk = book_values[i:i+100]
            f.write(f"INSERT INTO BIB_Buch (id, titel) VALUES {', '.join(chunk)};\n")
            
        # Insert link table
        f.write("\n-- Linking Authors and Books\n")
        link_values = []
        for bid, b in enumerate(books):
            aid = author_id_map[b['author']]
            link_values.append(f"({aid}, {bid+1})")
            
        for i in range(0, len(link_values), 100):
            chunk = link_values[i:i+100]
            f.write(f"INSERT INTO BIB_Autorin_Buch (autorin_id, buch_id) VALUES {', '.join(chunk)};\n")
            
        # Insert book copies (BIB_Buchexemplar)
        f.write("\n-- Inserting Book Copies (Buchexemplare)\n")
        copy_values = []
        for bid in range(len(books)):
            code = 200000 + bid
            copy_values.append(f"({code}, {bid+1})")
            
        for i in range(0, len(copy_values), 100):
            chunk = copy_values[i:i+100]
            f.write(f"INSERT INTO BIB_Buchexemplar (code, buch_id) VALUES {', '.join(chunk)};\n")
            
    print(f"Successfully generated {sql_file}!")

if __name__ == '__main__':
    main()
